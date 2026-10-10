package com.bancoxyz.cuentas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Registro de idempotencia: una fila por cada operacion de dinero ya aplicada, identificada por
 * una clave que entrega quien la solicita (el {@code transferenciaId} + paso de la saga, o el
 * {@code idOperacion} de una llamada REST de pagos-service).
 *
 * <p>Es la pieza que hace seguro reintentar. Kafka entrega "al menos una vez" y pagos-service
 * reintenta sus llamadas REST ante un timeout (Resilience4j Retry): sin este registro, un mismo
 * debito podria aplicarse dos veces. Como la fila se inserta en la MISMA transaccion local que
 * modifica el saldo, o quedan ambas cosas o ninguna; un reintento encuentra la fila y devuelve
 * el resultado ya conocido sin volver a tocar el saldo.</p>
 *
 * <p>Implementa {@link Persistable} para que {@code save()} haga siempre un INSERT directo
 * ({@code persist}) y nunca un {@code merge}: con un id asignado a mano, Spring Data no puede
 * saber si la fila es nueva, y un {@code merge} sobre una clave ya existente la ACTUALIZARIA en
 * silencio. Con INSERT directo, si dos entregas simultaneas del mismo evento llegan a competir,
 * la segunda choca contra la clave primaria, su transaccion completa se revierte (incluido el
 * movimiento de saldo) y, al reintentarse, encuentra la operacion ya registrada.</p>
 */
@Entity
@Table(name = "operaciones_procesadas")
public class OperacionProcesada implements Persistable<String> {

    public static final String APLICADA = "APLICADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String COMPENSADA = "COMPENSADA";

    @Id
    private String id;

    @Column(nullable = false)
    private String resultado;

    private String detalle;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    /** No se persiste: indica a Spring Data si esta instancia aun no existe en la base de datos. */
    @Transient
    private boolean nueva = true;

    protected OperacionProcesada() {
        // requerido por JPA
    }

    public OperacionProcesada(String id, String resultado, String detalle) {
        this.id = id;
        this.resultado = resultado;
        this.detalle = detalle;
        this.creadoEn = LocalDateTime.now();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.nueva = false;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
