package com.bancoxyz.pagos.evento;

import com.bancoxyz.pagos.service.PagoService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Cierra el ciclo de la saga del lado de pagos-service: consume el evento final publicado por
 * cuentas-service y actualiza el estado del pago (COMPLETADA, RECHAZADA o COMPENSADA).
 *
 * <p>Usa un consumer group propio ({@code pagos-service}), distinto del de
 * notificaciones-service: ambos reciben TODOS los resultados, cada uno para su proposito. Dentro
 * de este grupo, en cambio, las replicas de pagos-service se reparten las particiones.</p>
 */
@Component
public class ResultadoTransferenciaListener {

    private final PagoService pagoService;

    public ResultadoTransferenciaListener(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @KafkaListener(
            topics = "${kafka.topics.transferencias-resultado}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "resultadoListenerContainerFactory")
    public void onResultado(TransferenciaResultadoEvent evento) {
        pagoService.aplicarResultadoTransferencia(evento.transferenciaId(), evento.estado(), evento.detalle());
    }
}
