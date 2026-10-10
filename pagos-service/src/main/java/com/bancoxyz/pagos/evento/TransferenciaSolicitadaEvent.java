package com.bancoxyz.pagos.evento;

/** Evento que inicia la saga de transferencias ({@code bancoxyz.transferencias.solicitadas}); lo consume cuentas-service. */
public record TransferenciaSolicitadaEvent(
        String transferenciaId,
        long cuentaOrigen,
        long cuentaDestino,
        double monto
) {
}
