package com.bancoxyz.pagos.dto;

import com.bancoxyz.pagos.model.Pago;

/**
 * Estado actual de una transferencia ({@code GET /internal/transferencias/{id}}). Es un modelo
 * de lectura: una proyeccion que este servicio mantiene al dia a partir de los eventos de la
 * saga, para responder "como va mi transferencia" sin releer el historial de eventos.
 */
public record TransferenciaEstadoDTO(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto,
        String estado,
        String detalle
) {
    public static TransferenciaEstadoDTO desde(Pago pago) {
        return new TransferenciaEstadoDTO(pago.getId(),
                pago.getCuentaOrigen() == null ? 0L : pago.getCuentaOrigen(),
                pago.getCuentaDestino() == null ? 0L : pago.getCuentaDestino(),
                pago.getMonto(), pago.getEstado(), pago.getDetalle());
    }
}
