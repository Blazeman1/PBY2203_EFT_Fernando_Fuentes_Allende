package com.bancoxyz.bff.notificaciones.listener;

import com.bancoxyz.bff.notificaciones.dto.PagoCompletadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Notifica al cliente los depositos y pagos de servicios completados (topico {@code bancoxyz.pagos.completados}). */
@Component
public class PagoCompletadoListener {

    private static final Logger log = LoggerFactory.getLogger(PagoCompletadoListener.class);

    private final IdentidadInstancia instancia;

    public PagoCompletadoListener(IdentidadInstancia instancia) {
        this.instancia = instancia;
    }

    @KafkaListener(
            topics = "${kafka.topics.pagos-completados}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "pagoCompletadoListenerContainerFactory")
    public void onPagoCompletado(
            @Payload PagoCompletadoEvent evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("[instancia {}] Pago notificado (particion={}, offset={}) - pagoId={} tipo={} cuenta={} -> " +
                        "\"Se registro un movimiento de ${} en su cuenta: {}.\"",
                instancia.etiqueta(), particion, offset, evento.pagoId(), evento.tipo(), evento.cuentaId(),
                evento.monto(), evento.glosa());
    }
}
