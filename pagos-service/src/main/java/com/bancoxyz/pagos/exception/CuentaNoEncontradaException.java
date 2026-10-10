package com.bancoxyz.pagos.exception;

public class CuentaNoEncontradaException extends RuntimeException {
    public CuentaNoEncontradaException(long cuentaId) {
        super("No existe una cuenta valida con id=" + cuentaId);
    }
}
