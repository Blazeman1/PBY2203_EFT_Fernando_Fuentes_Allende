package com.bancoxyz.bff.notificaciones.listener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Nombre legible de ESTA instancia para los logs: "host:puerto". En docker-compose el host es el
 * identificador del contenedor, por lo que cada replica queda distinguible aunque todas escuchen
 * en el mismo puerto interno; es lo que permite ver en la evidencia que replica proceso cada mensaje.
 */
@Component
public class IdentidadInstancia {

    private final String etiqueta;

    public IdentidadInstancia(@Value("${server.port}") String puerto) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "desconocido";
        }
        this.etiqueta = host + ":" + puerto;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
