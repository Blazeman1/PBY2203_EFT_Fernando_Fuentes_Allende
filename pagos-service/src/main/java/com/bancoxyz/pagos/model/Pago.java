package com.bancoxyz.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Registro de una operacion de pago (transferencia, deposito o pago de servicio) y de su estado.
 * El identificador es un UUID generado por este servicio; para una transferencia es, ademas, el
 * {@code transferenciaId} que viaja en todos los eventos de la saga.
 */
@Entity
@Table(name = "pagos")
public class Pago {

    public static final String TIPO_TRANSFERENCIA = "TRANSFERENCIA";
    public static final String TIPO_DEPOSITO = "DEPOSITO";
    public static final String TIPO_PAGO_SERVICIO = "PAGO_SERVICIO";

    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String COMPENSADA = "COMPENSADA";
    /** No se pudo procesar por una falla de infraestructura (no por una regla de negocio). */
    public static final String FALLIDA = "FALLIDA";

    @Id
    private String id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "cuenta_origen")
    private Long cuentaOrigen;

    @Column(name = "cuenta_destino")
    private Long cuentaDestino;

    @Column(nullable = false)
    private double monto;

    @Column(nullable = false)
    private String estado;

    private String detalle;

    private String glosa;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Version
    private Long version;

    protected Pago() {
        // requerido por JPA
    }

    public Pago(String id, String tipo, Long cuentaOrigen, Long cuentaDestino, double monto, String glosa) {
        this.id = id;
        this.tipo = tipo;
        this.cuentaOrigen = cuentaOrigen;
        this.cuentaDestino = cuentaDestino;
        this.monto = monto;
        this.glosa = glosa;
        this.estado = EN_PROCESO;
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = this.creadoEn;
    }

    public boolean estaEnProceso() {
        return EN_PROCESO.equals(estado);
    }

    public void cambiarEstado(String nuevoEstado, String nuevoDetalle) {
        this.estado = nuevoEstado;
        this.detalle = nuevoDetalle;
        this.actualizadoEn = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getCuentaOrigen() {
        return cuentaOrigen;
    }

    public Long getCuentaDestino() {
        return cuentaDestino;
    }

    public double getMonto() {
        return monto;
    }

    public String getEstado() {
        return estado;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getGlosa() {
        return glosa;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public Long getVersion() {
        return version;
    }
}
