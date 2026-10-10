package com.bancoxyz.pagos.service;

import com.bancoxyz.pagos.client.CuentasServiceClient;
import com.bancoxyz.pagos.dto.DepositoRequest;
import com.bancoxyz.pagos.dto.PagoDTO;
import com.bancoxyz.pagos.dto.PagoServicioRequest;
import com.bancoxyz.pagos.dto.TransferenciaRequest;
import com.bancoxyz.pagos.evento.AlertaSeguridadEvent;
import com.bancoxyz.pagos.evento.EventoPublisher;
import com.bancoxyz.pagos.evento.PagoCompletadoEvent;
import com.bancoxyz.pagos.evento.TransferenciaSolicitadaEvent;
import com.bancoxyz.pagos.exception.CuentaNoEncontradaException;
import com.bancoxyz.pagos.exception.CuentasServiceNoDisponibleException;
import com.bancoxyz.pagos.exception.MensajeriaNoDisponibleException;
import com.bancoxyz.pagos.exception.OperacionNoPermitidaException;
import com.bancoxyz.pagos.exception.SaldoInsuficienteException;
import com.bancoxyz.pagos.exception.SolicitudInvalidaException;
import com.bancoxyz.pagos.model.Pago;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Coordina el flujo de cada tipo de operacion. En los tres casos el patron es el mismo:
 * (1) se REGISTRA el pago como EN_PROCESO antes de hablar con nadie, (2) se intenta la operacion
 * contra el sistema que corresponde y (3) se registra el desenlace. Asi, pase lo que pase en el
 * paso 2 -incluida una caida a mitad de camino- siempre queda constancia de que la operacion se
 * solicito y en que estado quedo.
 */
@Component
public class ProcesadorPagos {

    private final PagoService pagoService;
    private final CuentasServiceClient cuentasServiceClient;
    private final EventoPublisher eventoPublisher;
    private final String topicoPagosCompletados;
    private final String topicoAlertas;
    private final double umbralMontoAlto;

    public ProcesadorPagos(PagoService pagoService,
                           CuentasServiceClient cuentasServiceClient,
                           EventoPublisher eventoPublisher,
                           @Value("${kafka.topics.pagos-completados}") String topicoPagosCompletados,
                           @Value("${kafka.topics.alertas-seguridad}") String topicoAlertas,
                           @Value("${pagos.alertas.umbral-monto-alto}") double umbralMontoAlto) {
        this.pagoService = pagoService;
        this.cuentasServiceClient = cuentasServiceClient;
        this.eventoPublisher = eventoPublisher;
        this.topicoPagosCompletados = topicoPagosCompletados;
        this.topicoAlertas = topicoAlertas;
        this.umbralMontoAlto = umbralMontoAlto;
    }

    /**
     * Transferencia: ASINCRONA. Se registra el pago y se publica el evento que inicia la saga; el
     * desenlace llega despues por Kafka ({@code ResultadoTransferenciaListener}). Si el broker no
     * esta disponible, el pago queda como FALLIDA y el llamante recibe un 503.
     */
    public PagoDTO solicitarTransferencia(TransferenciaRequest request) {
        if (request.cuentaOrigen().equals(request.cuentaDestino())) {
            throw new SolicitudInvalidaException("La cuenta origen y la cuenta destino no pueden ser la misma.");
        }
        PagoDTO pago = pagoService.registrar(Pago.TIPO_TRANSFERENCIA, request.cuentaOrigen(), request.cuentaDestino(),
                request.monto(), "Transferencia entre cuentas");
        try {
            eventoPublisher.publicarSolicitud(new TransferenciaSolicitadaEvent(
                    pago.pagoId(), request.cuentaOrigen(), request.cuentaDestino(), request.monto()));
        } catch (MensajeriaNoDisponibleException e) {
            pagoService.cambiarEstado(pago.pagoId(), Pago.FALLIDA, "No se pudo iniciar la transferencia: mensajeria no disponible.");
            throw e;
        }

        if (request.monto() >= umbralMontoAlto) {
            eventoPublisher.publicarInformativo(topicoAlertas, String.valueOf(request.cuentaOrigen()),
                    new AlertaSeguridadEvent(UUID.randomUUID().toString(), "TRANSFERENCIA_MONTO_ALTO", "ALTA", null,
                            request.cuentaOrigen(),
                            "Transferencia " + pago.pagoId() + " por $" + request.monto() + " supera el umbral de monitoreo ($"
                                    + umbralMontoAlto + ").",
                            "pagos-service", LocalDateTime.now().toString()));
        }
        return pago;
    }

    /** Deposito: SINCRONICO. Acredita la cuenta destino en cuentas-service. */
    public PagoDTO depositar(DepositoRequest request) {
        String glosa = request.glosa() == null || request.glosa().isBlank() ? "Deposito" : request.glosa().trim();
        PagoDTO pago = pagoService.registrar(Pago.TIPO_DEPOSITO, null, request.cuentaDestino(), request.monto(), glosa);
        try {
            cuentasServiceClient.acreditar(request.cuentaDestino(), request.monto(), "deposito", glosa, pago.pagoId());
        } catch (CuentaNoEncontradaException | OperacionNoPermitidaException e) {
            return pagoService.cambiarEstado(pago.pagoId(), Pago.RECHAZADA, e.getMessage());
        } catch (CuentasServiceNoDisponibleException e) {
            pagoService.cambiarEstado(pago.pagoId(), Pago.FALLIDA, e.getMessage());
            throw e;
        }
        return completar(pago, request.cuentaDestino());
    }

    /** Pago de servicio: SINCRONICO. Debita la cuenta origen en cuentas-service. */
    public PagoDTO pagarServicio(PagoServicioRequest request) {
        String glosa = "Pago de servicio: " + request.comercio().trim();
        PagoDTO pago = pagoService.registrar(Pago.TIPO_PAGO_SERVICIO, request.cuentaOrigen(), null, request.monto(), glosa);
        try {
            cuentasServiceClient.debitar(request.cuentaOrigen(), request.monto(), "pago", glosa, pago.pagoId());
        } catch (CuentaNoEncontradaException | SaldoInsuficienteException | OperacionNoPermitidaException e) {
            return pagoService.cambiarEstado(pago.pagoId(), Pago.RECHAZADA, e.getMessage());
        } catch (CuentasServiceNoDisponibleException e) {
            pagoService.cambiarEstado(pago.pagoId(), Pago.FALLIDA, e.getMessage());
            throw e;
        }
        return completar(pago, request.cuentaOrigen());
    }

    private PagoDTO completar(PagoDTO pago, long cuentaId) {
        PagoDTO completado = pagoService.cambiarEstado(pago.pagoId(), Pago.COMPLETADA, null);
        eventoPublisher.publicarInformativo(topicoPagosCompletados, String.valueOf(cuentaId),
                new PagoCompletadoEvent(completado.pagoId(), completado.tipo(), cuentaId, completado.monto(),
                        completado.glosa(), LocalDateTime.now().toString()));
        return completado;
    }
}
