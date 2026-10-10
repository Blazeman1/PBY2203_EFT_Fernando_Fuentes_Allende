package com.bancoxyz.bff.atm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositoRequest(@NotNull @Positive Double monto) {
}
