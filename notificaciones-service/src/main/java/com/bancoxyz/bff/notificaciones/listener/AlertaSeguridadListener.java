package com.bancoxyz.bff.notificaciones.listener;

import com.bancoxyz.bff.notificaciones.dto.AlertaSeguridadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Procesa en tiempo real las alertas de seguridad (topico {@code bancoxyz.seguridad.alertas}):
 * transferencias de monto alto (pagos-service) y cambios en datos sensibles de un cliente
 * (clientes-service). Simula el aviso al equipo de monitoreo de fraude; las de severidad ALTA
 * se registran como WARN para que destaquen en los logs y en cualquier herramienta de alertas.
 */
@Component
public class AlertaSeguridadListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaSeguridadListener.class);

    private final IdentidadInstancia instancia;

    public AlertaSeguridadListener(IdentidadInstancia instancia) {
        this.instancia = instancia;
    }

    @KafkaListener(
            topics = "${kafka.topics.alertas-seguridad}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "alertaSeguridadListenerContainerFactory")
    public void onAlerta(
            @Payload AlertaSeguridadEvent evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        String linea = String.format("[instancia %s] ALERTA DE SEGURIDAD (particion=%d, offset=%d) - tipo=%s severidad=%s " +
                        "origen=%s clienteId=%s cuentaId=%s -> \"%s\"",
                instancia.etiqueta(), particion, offset, evento.tipo(), evento.severidad(), evento.origen(),
                evento.clienteId(), evento.cuentaId(), evento.detalle());
        if ("ALTA".equals(evento.severidad())) {
            log.warn(linea);
        } else {
            log.info(linea);
        }
    }
}
