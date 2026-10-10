package com.bancoxyz.cuentas.exception;

public class ClientesServiceNoDisponibleException extends RuntimeException {
    public ClientesServiceNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
