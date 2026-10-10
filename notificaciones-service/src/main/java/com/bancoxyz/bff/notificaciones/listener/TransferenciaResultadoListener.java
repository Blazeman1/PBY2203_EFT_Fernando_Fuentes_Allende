package com.bancoxyz.bff.notificaciones.listener;

import com.bancoxyz.bff.notificaciones.dto.TransferenciaResultadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor desacoplado del camino transaccional critico: simula el envio de una notificacion
 * al cliente cuando la saga de una transferencia termina, sea cual sea el resultado. No puede
 * afectar ese resultado (la transferencia ya quedo resuelta antes de publicarse este evento): si
 * este servicio estuviera caido, la saga igual se completa; solo se retrasa la notificacion,
 * nunca se compromete la consistencia del saldo.
 *
 * <p>Cada linea de log incluye la instancia, la particion y el offset: es la evidencia de que,
 * con varias replicas en el mismo consumer group, Kafka reparte las particiones del topico entre
 * ellas (escalabilidad horizontal del consumo) en vez de entregar cada mensaje a todas.</p>
 */
@Component
public class TransferenciaResultadoListener {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaResultadoListener.class);

    private final IdentidadInstancia instancia;

    public TransferenciaResultadoListener(IdentidadInstancia instancia) {
        this.instancia = instancia;
    }

    @KafkaListener(
            topics = "${kafka.topics.transferencias-resultado}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onResultado(
            @Payload TransferenciaResultadoEvent evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        String mensaje = switch (evento.estado()) {
            case "COMPLETADA" -> "Su transferencia de $" + evento.monto() + " a la cuenta " + evento.cuentaDestino()
                    + " se realizo con exito.";
            case "RECHAZADA" -> "Su transferencia de $" + evento.monto() + " no pudo procesarse: " + evento.detalle();
            case "COMPENSADA" -> "Su transferencia de $" + evento.monto() + " fue revertida: " + evento.detalle();
            default -> "Actualizacion de su transferencia " + evento.transferenciaId() + ": " + evento.estado();
        };
        log.info("[instancia {}] Notificacion procesada (particion={}, offset={}) - transferenciaId={} " +
                        "cuentaOrigen={} estado={} -> \"{}\"",
                instancia.etiqueta(), particion, offset, evento.transferenciaId(), evento.cuentaOrigen(),
                evento.estado(), mensaje);
    }
}
