package com.bancoxyz.bff.atm.exception;

/** pagos-service no responde: el cajero informa que la operacion no pudo realizarse. */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
