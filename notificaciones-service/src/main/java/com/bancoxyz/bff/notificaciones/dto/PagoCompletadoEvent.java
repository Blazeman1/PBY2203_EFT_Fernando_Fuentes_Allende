package com.bancoxyz.bff.notificaciones.dto;

/** Copia local del evento {@code bancoxyz.pagos.completados} que publica pagos-service (depositos y pagos de servicios). */
public record PagoCompletadoEvent(
        String pagoId,
        String tipo,
        long cuentaId,
        double monto,
        String glosa,
        String fechaHora
) {
}
