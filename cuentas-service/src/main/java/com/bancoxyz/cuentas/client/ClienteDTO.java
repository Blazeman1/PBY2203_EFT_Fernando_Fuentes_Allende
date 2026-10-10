package com.bancoxyz.cuentas.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subconjunto del contrato de clientes-service que este servicio necesita (copia local, no codigo compartido). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteDTO(long clienteId, String nombre, String estado) {

    public boolean estaActivo() {
        return "ACTIVO".equals(estado);
    }
}
