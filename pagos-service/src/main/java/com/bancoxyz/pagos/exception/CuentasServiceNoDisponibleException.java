package com.bancoxyz.pagos.exception;

public class CuentasServiceNoDisponibleException extends RuntimeException {
    public CuentasServiceNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
