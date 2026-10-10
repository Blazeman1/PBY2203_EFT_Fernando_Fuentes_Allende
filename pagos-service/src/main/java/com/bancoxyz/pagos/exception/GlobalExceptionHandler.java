package com.bancoxyz.pagos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PagoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> manejar(PagoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Map<String, String>> manejar(SolicitudInvalidaException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    /** Falla de infraestructura (cuentas-service caido): 503 controlado, no una traza cruda. */
    @ExceptionHandler(CuentasServiceNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejar(CuentasServiceNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    /** Falla de infraestructura (broker Kafka caido). */
    @ExceptionHandler(MensajeriaNoDisponibleException.class)
    public ResponseEntity<Map<String, String>> manejar(MensajeriaNoDisponibleException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }
}
