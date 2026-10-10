package com.bancoxyz.bff.web.pagos;

import com.bancoxyz.bff.web.client.PagoInternoDTO;

import java.time.LocalDateTime;

/** Vista de un pago para el canal web: detalle completo, apto para una tabla de historial. */
public record PagoWebResponse(
        String pagoId,
        String tipo,
        Long cuentaOrigen,
        Long cuentaDestino,
        double monto,
        String estado,
        String detalle,
        String glosa,
        LocalDateTime fecha
) {
    public static PagoWebResponse desde(PagoInternoDTO pago) {
        return new PagoWebResponse(pago.pagoId(), pago.tipo(), pago.cuentaOrigen(), pago.cuentaDestino(), pago.monto(),
                pago.estado(), pago.detalle(), pago.glosa(), pago.creadoEn());
    }
}
