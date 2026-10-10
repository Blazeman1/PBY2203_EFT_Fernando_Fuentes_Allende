package com.bancoxyz.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Pago de un servicio (luz, agua, telefonia...) con cargo a una cuenta. */
public record PagoServicioRequest(
        @NotNull Long cuentaOrigen,
        @NotNull @Positive Double monto,
        @NotBlank @Size(max = 200) String comercio
) {
}
