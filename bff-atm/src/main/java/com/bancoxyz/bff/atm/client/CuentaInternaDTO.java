package com.bancoxyz.bff.atm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Espejo LOCAL, y deliberadamente incompleto, del detalle de cuenta de cuentas-service: este
 * canal jamas necesita el historial de movimientos ni el identificador del cliente, asi que esos
 * campos ni siquiera se declaran aqui. Es la forma mas simple de que este BFF nunca llegue a
 * tener en memoria datos que su canal no deberia manejar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaInternaDTO(long cuentaId, String tipo, String estado, double saldo) {
}
