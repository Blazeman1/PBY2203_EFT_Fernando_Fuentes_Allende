package com.bancoxyz.cuentas.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Parsea las fechas del legacy, que llegan en cuatro formatos distintos: {@code yyyy-MM-dd},
 * {@code yyyy/MM/dd}, {@code dd-MM-yyyy} y {@code dd/MM/yyyy}. Mismo criterio que el modulo
 * batch (Parte 1), para que ambos interpreten los datos del legacy de forma equivalente.
 */
public final class FechaFlexibleParser {

    private static final DateTimeFormatter[] FORMATOS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    private FechaFlexibleParser() {
    }

    /** Devuelve la fecha parseada, o vacio si el valor no calza con ningun formato soportado. */
    public static Optional<LocalDate> parsear(String valorCrudo) {
        if (valorCrudo == null || valorCrudo.isBlank()) {
            return Optional.empty();
        }
        String valor = valorCrudo.trim();
        for (DateTimeFormatter formato : FORMATOS) {
            try {
                return Optional.of(LocalDate.parse(valor, formato));
            } catch (DateTimeParseException ignorada) {
                // se intenta el siguiente formato soportado
            }
        }
        return Optional.empty();
    }
}
