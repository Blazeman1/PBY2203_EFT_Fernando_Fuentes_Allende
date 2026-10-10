package com.bancoxyz.bff.web.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/** Espejo local del registro de pago que expone pagos-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoInternoDTO(
        String pagoId,
        String tipo,
        Long cuentaOrigen,
        Long cuentaDestino,
        double monto,
        String estado,
        String detalle,
        String glosa,
        LocalDateTime creadoEn
) {
}
