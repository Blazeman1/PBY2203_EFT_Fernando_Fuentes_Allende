package com.bancoxyz.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Alta de un cliente nuevo. El rango de edad replica la regla de validacion aplicada al legacy. */
public record CrearClienteRequest(
        @NotBlank String nombre,
        @NotNull @Min(18) @Max(90) Integer edad,
        @Email String email,
        String telefono,
        String direccion
) {
}
