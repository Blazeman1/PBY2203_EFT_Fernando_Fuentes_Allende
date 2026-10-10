package com.bancoxyz.bff.web.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Espejo local del perfil de cliente que expone clientes-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteInternoDTO(
        long clienteId,
        String nombre,
        int edad,
        String email,
        String telefono,
        String direccion,
        String estado
) {
}
