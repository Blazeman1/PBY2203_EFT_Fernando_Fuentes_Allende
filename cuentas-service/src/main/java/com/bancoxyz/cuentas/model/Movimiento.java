package com.bancoxyz.cuentas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Movimiento de una cuenta. Combina el historial importado del legacy
 * ({@code estados_financieros_anuales.csv}) con los movimientos que genera el sistema nuevo cada
 * vez que se debita o acredita un saldo: a diferencia de la version en memoria anterior, toda
 * operacion de dinero deja ahora su propio registro auditable.
 */
@Entity
@Table(name = "movimientos")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "tipo_movimiento", nullable = false)
    private String tipoMovimiento;

    @Column(nullable = false)
    private double monto;

    private String descripcion;

    protected Movimiento() {
        // requerido por JPA
    }

    public Movimiento(Long cuentaId, LocalDate fecha, String tipoMovimiento, double monto, String descripcion) {
        this.cuentaId = cuentaId;
        this.fecha = fecha;
        this.tipoMovimiento = tipoMovimiento;
        this.monto = monto;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getTipoMovimiento() {
        return tipoMovimiento;
    }

    public double getMonto() {
        return monto;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
