package com.bancoxyz.bff.atm.exception;

/** cuentas-service rechazo la operacion por una regla de negocio (por ejemplo, la cuenta esta cerrada). */
public class OperacionNoPermitidaException extends RuntimeException {
    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
