package com.bancoxyz.bff.atm.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> manejar(CuentaNoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AutenticacionException.class)
    public ResponseEntity<Map<String, String>> manejar(AutenticacionException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SesionInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejar(SesionInvalidaException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<Map<String, String>> manejar(SaldoInsuficienteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(LimiteRetiroExcedidoException.class)
    public ResponseEntity<Map<String, String>> manejar(LimiteRetiroExcedidoException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CuentasServiceNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejar(CuentasServiceNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", e.getMessage()));
    }

    /** Fallback del circuit breaker de pagos-service: 503 controlado. */
    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejar(ServicioNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    public ResponseEntity<Map<String, String>> manejar(OperacionNoPermitidaException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DepositoRechazadoException.class)
    public ResponseEntity<Map<String, String>> manejar(DepositoRechazadoException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
    }
}
