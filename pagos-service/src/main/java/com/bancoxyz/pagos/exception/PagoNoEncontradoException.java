package com.bancoxyz.pagos.exception;

public class PagoNoEncontradoException extends RuntimeException {
    public PagoNoEncontradoException(String pagoId) {
        super("No existe un pago con id=" + pagoId);
    }
}
