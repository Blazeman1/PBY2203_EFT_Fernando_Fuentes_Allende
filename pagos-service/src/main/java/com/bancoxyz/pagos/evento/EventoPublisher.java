package com.bancoxyz.pagos.evento;

import com.bancoxyz.pagos.exception.MensajeriaNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Unico punto de publicacion de eventos de pagos-service hacia Kafka, protegido con un circuit
 * breaker propio ({@code kafkaProducer}): una falla del broker es un dominio de falla distinto
 * de una caida de cuentas-service y no deben compartir contador ni circuito.
 *
 * <p>Distingue dos clases de evento, con comportamientos alternativos distintos ante una falla:</p>
 * <ul>
 *   <li><b>Criticos</b> ({@link #publicarSolicitud}): sin este evento la transferencia no
 *       existe, asi que la falla se propaga como {@link MensajeriaNoDisponibleException} y el
 *       llamante recibe un 503 controlado.</li>
 *   <li><b>Informativos</b> ({@link #publicarInformativo}: alertas de seguridad y avisos de
 *       pago completado): la operacion de negocio ya quedo confirmada, por lo que una falla al
 *       publicar solo se registra en el log y NO hace fallar la operacion.</li>
 * </ul>
 */
@Component
public class EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicoSolicitadas;

    public EventoPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                           @Value("${kafka.topics.transferencias-solicitadas}") String topicoSolicitadas) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicoSolicitadas = topicoSolicitadas;
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publicarSolicitudFallback")
    public void publicarSolicitud(TransferenciaSolicitadaEvent evento) {
        enviar(topicoSolicitadas, evento.transferenciaId(), evento);
    }

    private void publicarSolicitudFallback(TransferenciaSolicitadaEvent evento, Throwable t) {
        throw new MensajeriaNoDisponibleException(
                "El sistema de mensajeria no esta disponible en este momento; intente mas tarde.", t);
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publicarInformativoFallback")
    public void publicarInformativo(String topico, String clave, Object evento) {
        enviar(topico, clave, evento);
    }

    private void publicarInformativoFallback(String topico, String clave, Object evento, Throwable t) {
        log.warn("Evento informativo NO enviado a {} (mensajeria no disponible): {} causa={}", topico, evento, t.toString());
    }

    /** {@code send()} es asincrono: se espera la confirmacion del broker para que una falla sea visible. */
    private void enviar(String topico, String clave, Object evento) {
        try {
            kafkaTemplate.send(topico, clave, evento).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se publicaba en " + topico, e);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar el evento en " + topico, e);
        }
    }
}
