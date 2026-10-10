package com.bancoxyz.pagos.evento;

/**
 * Evento de "transaccion completada" ({@code bancoxyz.pagos.completados}) para las operaciones
 * sincronicas (depositos y pagos de servicios). Lo consume notificaciones-service.
 */
public record PagoCompletadoEvent(
        String pagoId,
        String tipo,
        long cuentaId,
        double monto,
        String glosa,
        String fechaHora
) {
}
