package com.bancoxyz.pagos.evento;

/**
 * Evento publicado en {@code bancoxyz.seguridad.alertas}. Mismo contrato JSON que usa
 * clientes-service para sus propias alertas; lo consume notificaciones-service.
 *
 * @param tipo      por ejemplo TRANSFERENCIA_MONTO_ALTO
 * @param severidad BAJA | MEDIA | ALTA
 * @param fechaHora instante de la alerta en formato ISO-8601 (texto, para no acoplar el contrato
 *                  a como cada microservicio serializa fechas)
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
