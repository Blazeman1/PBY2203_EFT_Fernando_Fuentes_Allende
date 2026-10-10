package com.bancoxyz.bff.atm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Espejo local, reducido a lo que el cajero necesita, del registro de pago de pagos-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoInternoDTO(String pagoId, String estado, String detalle) {
}
