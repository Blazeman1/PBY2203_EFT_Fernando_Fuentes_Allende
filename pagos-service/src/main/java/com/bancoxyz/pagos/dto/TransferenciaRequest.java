package com.bancoxyz.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferenciaRequest(
        @NotNull Long cuentaOrigen,
        @NotNull Long cuentaDestino,
        @NotNull @Positive Double monto
) {
}
