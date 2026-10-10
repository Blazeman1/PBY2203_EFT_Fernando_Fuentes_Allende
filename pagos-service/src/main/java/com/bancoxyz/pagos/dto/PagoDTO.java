package com.bancoxyz.pagos.dto;

import com.bancoxyz.pagos.model.Pago;

import java.time.LocalDateTime;

public record PagoDTO(
        String pagoId,
        String tipo,
        Long cuentaOrigen,
        Long cuentaDestino,
        double monto,
        String estado,
        String detalle,
        String glosa,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static PagoDTO desde(Pago pago) {
        return new PagoDTO(pago.getId(), pago.getTipo(), pago.getCuentaOrigen(), pago.getCuentaDestino(), pago.getMonto(),
                pago.getEstado(), pago.getDetalle(), pago.getGlosa(), pago.getCreadoEn(), pago.getActualizadoEn());
    }
}
