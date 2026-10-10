package com.bancoxyz.bff.web.perfil;

import com.bancoxyz.bff.web.client.ClienteInternoDTO;

/** Perfil del titular tal como lo ve el canal web (no expone el estado interno del cliente). */
public record PerfilWebResponse(String nombre, int edad, String email, String telefono, String direccion) {

    public static PerfilWebResponse desde(ClienteInternoDTO cliente) {
        return new PerfilWebResponse(cliente.nombre(), cliente.edad(), cliente.email(), cliente.telefono(), cliente.direccion());
    }
}
