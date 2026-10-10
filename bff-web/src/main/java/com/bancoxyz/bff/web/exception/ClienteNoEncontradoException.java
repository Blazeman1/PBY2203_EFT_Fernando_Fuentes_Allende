package com.bancoxyz.bff.web.exception;

public class ClienteNoEncontradoException extends RuntimeException {
    public ClienteNoEncontradoException(long clienteId) {
        super("No existe un cliente con id=" + clienteId);
    }
}
