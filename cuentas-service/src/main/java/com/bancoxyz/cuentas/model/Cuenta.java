package com.bancoxyz.cuentas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;

/**
 * Cuenta bancaria. El identificador se asigna explicitamente (no es autogenerado por JPA): las
 * cuentas migradas conservan el {@code cuenta_id} del sistema legacy, y las cuentas nuevas toman
 * su numero de la secuencia {@code cuenta_id_seq} de PostgreSQL (ver {@code CuentaService#abrir}).
 *
 * <p>{@link Version} habilita bloqueo optimista como red de seguridad para las operaciones de
 * mantenimiento; las operaciones que mueven dinero usan ademas un bloqueo pesimista de fila
 * ({@code SELECT ... FOR UPDATE}, ver {@code CuentaRepository#buscarParaActualizar}), que es lo
 * que garantiza un saldo correcto aunque haya varias replicas de este servicio atendiendo
 * peticiones sobre la misma cuenta al mismo tiempo.</p>
 */
@Entity
@Table(name = "cuentas")
public class Cuenta {

    public static final String ESTADO_ACTIVA = "ACTIVA";
    public static final String ESTADO_CERRADA = "CERRADA";

    @Id
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private double saldo;

    @Column(nullable = false)
    private String estado;

    @Column(name = "fecha_apertura")
    private LocalDate fechaApertura;

    @Version
    private Long version;

    protected Cuenta() {
        // requerido por JPA
    }

    public Cuenta(Long id, Long clienteId, String tipo, double saldo, LocalDate fechaApertura) {
        this.id = id;
        this.clienteId = clienteId;
        this.tipo = tipo;
        this.saldo = saldo;
        this.estado = ESTADO_ACTIVA;
        this.fechaApertura = fechaApertura;
    }

    public boolean estaActiva() {
        return ESTADO_ACTIVA.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public double getSaldo() {
        return saldo;
    }

    public void setSaldo(double saldo) {
        this.saldo = saldo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getFechaApertura() {
        return fechaApertura;
    }

    public Long getVersion() {
        return version;
    }
}
