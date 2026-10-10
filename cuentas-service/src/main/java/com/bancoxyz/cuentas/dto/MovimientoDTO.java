package com.bancoxyz.cuentas.dto;

import com.bancoxyz.cuentas.model.Movimiento;

import java.time.LocalDate;

public record MovimientoDTO(LocalDate fecha, String tipoMovimiento, double monto, String descripcion) {

    public static MovimientoDTO desde(Movimiento movimiento) {
        return new MovimientoDTO(movimiento.getFecha(), movimiento.getTipoMovimiento(),
                movimiento.getMonto(), movimiento.getDescripcion());
    }
}
