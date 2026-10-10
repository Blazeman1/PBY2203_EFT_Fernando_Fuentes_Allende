package com.bancoxyz.pagos.exception;

public class MensajeriaNoDisponibleException extends RuntimeException {
    public MensajeriaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
