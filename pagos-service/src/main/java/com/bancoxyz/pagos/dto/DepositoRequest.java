package com.bancoxyz.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DepositoRequest(
        @NotNull Long cuentaDestino,
        @NotNull @Positive Double monto,
        @Size(max = 200) String glosa
) {
}
