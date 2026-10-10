package com.bancoxyz.cuentas.saga.dto;

/**
 * Segundo evento de la saga ({@code bancoxyz.transferencias.debito-aplicado}): el debito en la
 * cuenta origen ya quedo confirmado en la base de datos y corresponde intentar el credito.
 */
public record DebitoAplicadoEvent(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto
) {
}
