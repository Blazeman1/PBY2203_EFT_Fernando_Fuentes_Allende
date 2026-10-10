package com.bancoxyz.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Apertura de una cuenta nueva para un cliente existente y activo en clientes-service. */
public record AperturaCuentaRequest(
        @NotNull Long clienteId,
        @NotBlank String tipo,
        @PositiveOrZero Double saldoInicial
) {
}
