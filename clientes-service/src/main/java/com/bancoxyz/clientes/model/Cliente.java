package com.bancoxyz.clientes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Cliente del banco. El identificador se asigna explicitamente: los clientes migrados conservan
 * el identificador derivado del legacy y los nuevos lo toman de la secuencia
 * {@code cliente_id_seq}. {@link Version} da bloqueo optimista: dos actualizaciones simultaneas
 * del mismo perfil (por ejemplo, desde dos replicas) no se pisan en silencio; la segunda falla.
 */
@Entity
@Table(name = "clientes")
public class Cliente {

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_INACTIVO = "INACTIVO";

    @Id
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int edad;

    private String email;

    private String telefono;

    private String direccion;

    @Column(nullable = false)
    private String estado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Version
    private Long version;

    protected Cliente() {
        // requerido por JPA
    }

    public Cliente(Long id, String nombre, int edad, String email, String telefono, String direccion) {
        this.id = id;
        this.nombre = nombre;
        this.edad = edad;
        this.email = email;
        this.telefono = telefono;
        this.direccion = direccion;
        this.estado = ESTADO_ACTIVO;
        this.creadoEn = LocalDateTime.now();
    }

    public boolean estaActivo() {
        return ESTADO_ACTIVO.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getEdad() {
        return edad;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public Long getVersion() {
        return version;
    }
}
