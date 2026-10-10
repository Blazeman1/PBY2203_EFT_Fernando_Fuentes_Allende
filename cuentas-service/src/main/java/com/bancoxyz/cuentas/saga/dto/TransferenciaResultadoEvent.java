package com.bancoxyz.cuentas.saga.dto;

/**
 * Evento final de la saga ({@code bancoxyz.transferencias.resultado}). Lo consumen pagos-service
 * (actualiza el estado del pago) y notificaciones-service (avisa al cliente), cada uno con su
 * propio consumer group.
 *
 * @param estado  COMPLETADA | RECHAZADA | COMPENSADA
 * @param detalle motivo legible (solo relevante si el estado no es COMPLETADA)
 */
public record TransferenciaResultadoEvent(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto,
        String estado,
        String detalle
) {
    public static final String COMPLETADA = "COMPLETADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String COMPENSADA = "COMPENSADA";
}
