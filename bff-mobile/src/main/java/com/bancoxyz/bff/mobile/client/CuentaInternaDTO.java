package com.bancoxyz.bff.mobile.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Espejo local del detalle de cuenta de cuentas-service, limitado a lo que el canal movil usa.
 * No declara el identificador del cliente: este canal no muestra datos personales (y su token
 * OAuth2 tampoco le permitiria consultarlos en clientes-service).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaInternaDTO(
        long cuentaId,
        String tipo,
        String estado,
        double saldo,
        List<MovimientoInternoDTO> movimientos
) {
}
