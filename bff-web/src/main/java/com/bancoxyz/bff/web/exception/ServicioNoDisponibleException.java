package com.bancoxyz.bff.web.exception;

/** Un microservicio del que depende este BFF (clientes-service o pagos-service) no responde. */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
