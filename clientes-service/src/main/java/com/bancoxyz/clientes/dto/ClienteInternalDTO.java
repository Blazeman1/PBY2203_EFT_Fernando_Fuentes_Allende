package com.bancoxyz.clientes.dto;

import com.bancoxyz.clientes.model.Cliente;

/** Perfil completo de un cliente, tal como lo expone la API interna. */
public record ClienteInternalDTO(
        long clienteId,
        String nombre,
        int edad,
        String email,
        String telefono,
        String direccion,
        String estado
) {
    public static ClienteInternalDTO desde(Cliente cliente) {
        return new ClienteInternalDTO(cliente.getId(), cliente.getNombre(), cliente.getEdad(), cliente.getEmail(),
                cliente.getTelefono(), cliente.getDireccion(), cliente.getEstado());
    }
}
