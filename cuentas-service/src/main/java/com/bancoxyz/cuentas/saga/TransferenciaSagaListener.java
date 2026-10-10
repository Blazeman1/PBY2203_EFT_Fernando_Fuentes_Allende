package com.bancoxyz.cuentas.saga;

import com.bancoxyz.cuentas.model.OperacionProcesada;
import com.bancoxyz.cuentas.saga.dto.DebitoAplicadoEvent;
import com.bancoxyz.cuentas.saga.dto.TransferenciaResultadoEvent;
import com.bancoxyz.cuentas.saga.dto.TransferenciaSolicitadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Participante de la <b>saga por coreografia</b> de transferencias entre cuentas.
 *
 * <pre>
 *   pagos-service   --(transferencias.solicitadas)-->   cuentas-service
 *   1) onSolicitada:     debita cuentaOrigen  -> publica DebitoAplicadoEvent
 *                        (si no se puede, publica el resultado RECHAZADA y termina)
 *   2) onDebitoAplicado: acredita cuentaDestino -> publica resultado COMPLETADA
 *                        (si el destino no existe o esta cerrado, COMPENSA el debito del paso 1
 *                        y publica resultado COMPENSADA)
 *   cuentas-service --(transferencias.resultado)-->  pagos-service + notificaciones-service
 * </pre>
 *
 * <p><b>Consistencia en un entorno distribuido.</b> No existe una transaccion ACID que abarque a
 * pagos-service y a cuentas-service (cada uno tiene su propia base de datos), asi que la
 * consistencia se obtiene por composicion: cada paso es una transaccion local
 * ({@link SagaPasosService}), cada paso es idempotente, y un paso fallido se repara con una
 * accion compensatoria explicita en vez de un rollback global.</p>
 *
 * <p>Si la publicacion del evento siguiente falla (broker caido), este metodo lanza una
 * excepcion: Kafka no confirma el offset, el mensaje se reintenta (cada 2 s, hasta 10 minutos:
 * ver {@code KafkaConfig}) y, gracias a la idempotencia del paso, el reintento solo repite la
 * publicacion, nunca el movimiento de dinero.</p>
 *
 * <p>Con varias replicas de este servicio en el mismo consumer group, Kafka reparte las
 * particiones entre ellas: cada transferencia (clave = transferenciaId) es procesada por una
 * sola replica a la vez, y el bloqueo de fila en PostgreSQL protege el saldo cuando dos
 * transferencias distintas tocan la misma cuenta desde replicas diferentes.</p>
 */
@Component
public class TransferenciaSagaListener {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaSagaListener.class);
    private static final long SEGUNDOS_ESPERA_PUBLICACION = 5;

    private final SagaPasosService pasos;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicoDebitoAplicado;
    private final String topicoResultado;

    public TransferenciaSagaListener(
            SagaPasosService pasos,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topics.transferencias-debito-aplicado}") String topicoDebitoAplicado,
            @Value("${kafka.topics.transferencias-resultado}") String topicoResultado) {
        this.pasos = pasos;
        this.kafkaTemplate = kafkaTemplate;
        this.topicoDebitoAplicado = topicoDebitoAplicado;
        this.topicoResultado = topicoResultado;
    }

    @KafkaListener(
            topics = "${kafka.topics.transferencias-solicitadas}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "solicitudListenerContainerFactory")
    public void onSolicitada(TransferenciaSolicitadaEvent evento) {
        log.info("Saga transferencia {}: solicitud recibida (origen={}, destino={}, monto={})",
                evento.transferenciaId(), evento.cuentaOrigen(), evento.cuentaDestino(), evento.monto());
        OperacionProcesada paso = pasos.debitarOrigen(evento);

        if (OperacionProcesada.APLICADA.equals(paso.getResultado())) {
            publicar(topicoDebitoAplicado, evento.transferenciaId(), new DebitoAplicadoEvent(
                    evento.transferenciaId(), evento.cuentaOrigen(), evento.cuentaDestino(), evento.monto()));
        } else {
            // Rechazo temprano: nunca se llego a debitar, asi que no hay nada que compensar.
            publicar(topicoResultado, evento.transferenciaId(), new TransferenciaResultadoEvent(
                    evento.transferenciaId(), evento.cuentaOrigen(), evento.cuentaDestino(), evento.monto(),
                    TransferenciaResultadoEvent.RECHAZADA, paso.getDetalle()));
        }
    }

    @KafkaListener(
            topics = "${kafka.topics.transferencias-debito-aplicado}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "debitoAplicadoListenerContainerFactory")
    public void onDebitoAplicado(DebitoAplicadoEvent evento) {
        OperacionProcesada paso = pasos.acreditarDestino(evento);
        String estadoFinal = OperacionProcesada.APLICADA.equals(paso.getResultado())
                ? TransferenciaResultadoEvent.COMPLETADA
                : TransferenciaResultadoEvent.COMPENSADA;
        publicar(topicoResultado, evento.transferenciaId(), new TransferenciaResultadoEvent(
                evento.transferenciaId(), evento.cuentaOrigen(), evento.cuentaDestino(), evento.monto(),
                estadoFinal, paso.getDetalle()));
    }

    /** Publica y ESPERA la confirmacion del broker: un envio fallido debe hacer fallar (y reintentar) el paso. */
    private void publicar(String topico, String clave, Object evento) {
        try {
            kafkaTemplate.send(topico, clave, evento).get(SEGUNDOS_ESPERA_PUBLICACION, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se publicaba en " + topico, e);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar el evento en " + topico + "; se reintentara.", e);
        }
    }
}
