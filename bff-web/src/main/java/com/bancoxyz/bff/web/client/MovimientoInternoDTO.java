package com.bancoxyz.bff.web.client;

import java.time.LocalDate;

public record MovimientoInternoDTO(LocalDate fecha, String tipoMovimiento, double monto, String descripcion) {
}
