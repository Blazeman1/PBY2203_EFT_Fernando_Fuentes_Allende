package com.bancoxyz.cuentas.exception;

/** Regla de negocio violada (cuenta cerrada, cierre con saldo pendiente, tipo no soportado, cliente inactivo). */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
