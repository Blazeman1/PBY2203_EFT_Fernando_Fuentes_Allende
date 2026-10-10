package com.bancoxyz.bff.atm.exception;

/** pagos-service registro el deposito pero lo rechazo por una regla de negocio (por ejemplo, cuenta cerrada). */
public class DepositoRechazadoException extends RuntimeException {
    public DepositoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
