package com.bancoxyz.clientes.dto;

import jakarta.validation.constraints.Email;

/** Actualizacion de datos de contacto. Un campo nulo significa "no modificar". */
public record ActualizarPerfilRequest(
        @Email String email,
        String telefono,
        String direccion
) {
}
