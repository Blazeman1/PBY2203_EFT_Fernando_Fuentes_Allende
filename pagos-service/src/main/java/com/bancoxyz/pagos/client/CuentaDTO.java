package com.bancoxyz.pagos.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subconjunto del contrato de cuentas-service que este servicio necesita (copia local). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaDTO(long cuentaId, double saldo, String estado) {
}
