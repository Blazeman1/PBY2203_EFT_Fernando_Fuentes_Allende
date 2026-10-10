package com.bancoxyz.pagos.evento;

/**
 * Evento final de la saga ({@code bancoxyz.transferencias.resultado}), publicado por
 * cuentas-service. Copia local del contrato JSON.
 *
 * @param estado COMPLETADA | RECHAZADA | COMPENSADA
 */
public record TransferenciaResultadoEvent(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto,
        String estado,
        String detalle
) {
}
