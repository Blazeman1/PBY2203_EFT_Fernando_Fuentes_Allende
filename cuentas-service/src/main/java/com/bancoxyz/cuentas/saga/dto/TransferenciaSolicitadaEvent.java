package com.bancoxyz.cuentas.saga.dto;

/**
 * Evento que inicia la saga. Lo publica pagos-service en
 * {@code bancoxyz.transferencias.solicitadas}. Copia local del contrato: los microservicios se
 * acoplan al FORMATO del mensaje (JSON), nunca a una clase Java de otro modulo.
 */
public record TransferenciaSolicitadaEvent(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto
) {
}
