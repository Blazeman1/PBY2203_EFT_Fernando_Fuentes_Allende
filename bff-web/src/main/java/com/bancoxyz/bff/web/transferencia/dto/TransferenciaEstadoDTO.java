package com.bancoxyz.bff.web.transferencia.dto;

/**
 * Espejo del estado de una transferencia que expone pagos-service via
 * {@code GET /internal/transferencias/{id}} (ver {@code PagosServiceClient}).
 */
public record TransferenciaEstadoDTO(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto,
        String estado,
        String detalle
) {
}
