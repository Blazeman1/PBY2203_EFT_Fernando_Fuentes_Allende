package com.bancoxyz.clientes.controller;

import com.bancoxyz.clientes.dto.ActualizarPerfilRequest;
import com.bancoxyz.clientes.dto.ClienteInternalDTO;
import com.bancoxyz.clientes.dto.CrearClienteRequest;
import com.bancoxyz.clientes.evento.AlertaSeguridadPublisher;
import com.bancoxyz.clientes.model.Cliente;
import com.bancoxyz.clientes.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API interna de Gestion de Clientes. Protegida por
 * {@link com.bancoxyz.clientes.config.ResourceServerConfig}: exige un JWT de auth-server con
 * scope {@code clientes.access}.
 *
 * <p>Las alertas de seguridad se publican DESPUES de que el servicio confirma la transaccion
 * (el metodo de {@link ClienteService} ya retorno): nunca se anuncia por Kafka un cambio que la
 * base de datos todavia podria deshacer.</p>
 */
@RestController
@RequestMapping("/internal/clientes")
public class ClienteInternalController {

    private final ClienteService clienteService;
    private final AlertaSeguridadPublisher alertas;

    public ClienteInternalController(ClienteService clienteService, AlertaSeguridadPublisher alertas) {
        this.clienteService = clienteService;
        this.alertas = alertas;
    }

    @GetMapping
    public List<ClienteInternalDTO> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{clienteId}")
    public ClienteInternalDTO obtener(@PathVariable long clienteId) {
        return clienteService.obtener(clienteId);
    }

    @PostMapping
    public ResponseEntity<ClienteInternalDTO> crear(@Valid @RequestBody CrearClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crear(request));
    }

    /** Actualiza datos de contacto y deja constancia del cambio como alerta de seguridad. */
    @PutMapping("/{clienteId}")
    public ClienteInternalDTO actualizarPerfil(@PathVariable long clienteId, @Valid @RequestBody ActualizarPerfilRequest request) {
        ClienteInternalDTO actualizado = clienteService.actualizarPerfil(clienteId, request);
        alertas.publicar("CAMBIO_DATOS_CONTACTO", "MEDIA", clienteId,
                "Se modificaron los datos de contacto del cliente " + clienteId + ".");
        return actualizado;
    }

    @PostMapping("/{clienteId}/desactivar")
    public ClienteInternalDTO desactivar(@PathVariable long clienteId) {
        ClienteInternalDTO actualizado = clienteService.cambiarEstado(clienteId, Cliente.ESTADO_INACTIVO);
        alertas.publicar("CLIENTE_DESACTIVADO", "ALTA", clienteId, "El cliente " + clienteId + " fue desactivado.");
        return actualizado;
    }

    @PostMapping("/{clienteId}/reactivar")
    public ClienteInternalDTO reactivar(@PathVariable long clienteId) {
        return clienteService.cambiarEstado(clienteId, Cliente.ESTADO_ACTIVO);
    }
}
