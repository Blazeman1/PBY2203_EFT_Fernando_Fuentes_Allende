package com.bancoxyz.bff.notificaciones.dto;

/**
 * Copia local del evento {@code bancoxyz.seguridad.alertas}. Lo publican pagos-service
 * (transferencias de monto alto) y clientes-service (cambios en datos sensibles de un cliente).
 */
public record AlertaSeguridadEvent(
        String alertaId,
        String tipo,
        String severidad,
        Long clienteId,
        Long cuentaId,
        String detalle,
        String origen,
        String fechaHora
) {
}
