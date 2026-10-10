package com.bancoxyz.cuentas.dto;

import jakarta.validation.constraints.NotBlank;

/** Mantenimiento de una cuenta existente: hoy, el cambio de tipo de producto. */
public record MantenimientoCuentaRequest(@NotBlank String tipo) {
}
