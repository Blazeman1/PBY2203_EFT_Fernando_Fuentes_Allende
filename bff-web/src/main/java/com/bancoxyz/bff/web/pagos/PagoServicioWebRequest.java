package com.bancoxyz.bff.web.pagos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** La cuenta de cargo no viaja en el body: se toma de la URL, ya verificada contra el JWT. */
public record PagoServicioWebRequest(@NotNull @Positive Double monto, @NotBlank @Size(max = 200) String comercio) {
}
