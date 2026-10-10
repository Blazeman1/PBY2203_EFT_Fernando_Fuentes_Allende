package com.bancoxyz.bff.atm.dto;

/** Respuesta minima del cajero: confirma el deposito y entrega el comprobante (id del pago). */
public record DepositoResponse(boolean depositoExitoso, double montoDepositado, String comprobante) {
}
