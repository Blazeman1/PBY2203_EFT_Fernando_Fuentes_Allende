package com.bancoxyz.cuentas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de debito o credito de saldo.
 *
 * @param monto          monto a mover, siempre positivo
 * @param tipoMovimiento opcional; etiqueta del movimiento que queda en el historial
 * @param descripcion    opcional; glosa del movimiento
 * @param idOperacion    opcional; clave de idempotencia. Si se repite una llamada con la misma
 *                       clave, el saldo NO se vuelve a modificar (ver {@code OperacionProcesada})
 */
public record ActualizarSaldoRequest(
        @NotNull @Positive Double monto,
        @Size(max = 40) String tipoMovimiento,
        @Size(max = 255) String descripcion,
        @Size(max = 120) String idOperacion
) {
}
