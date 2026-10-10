package com.bancoxyz.bff.mobile.exception;

public class CuentasServiceNoDisponibleException extends RuntimeException {
    public CuentasServiceNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
