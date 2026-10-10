package com.bancoxyz.clientes.service;

import com.bancoxyz.clientes.dto.ActualizarPerfilRequest;
import com.bancoxyz.clientes.dto.ClienteInternalDTO;
import com.bancoxyz.clientes.dto.CrearClienteRequest;
import com.bancoxyz.clientes.exception.ClienteNoEncontradoException;
import com.bancoxyz.clientes.model.Cliente;
import com.bancoxyz.clientes.repository.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reglas de negocio de Gestion de Clientes. Cada metodo publico es una transaccion local. */
@Service
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clienteRepository;
    private final JdbcTemplate jdbcTemplate;

    public ClienteService(ClienteRepository clienteRepository, JdbcTemplate jdbcTemplate) {
        this.clienteRepository = clienteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<ClienteInternalDTO> listar() {
        return clienteRepository.findAll(Sort.by("id")).stream().map(ClienteInternalDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public ClienteInternalDTO obtener(long clienteId) {
        return ClienteInternalDTO.desde(buscar(clienteId));
    }

    @Transactional
    public ClienteInternalDTO crear(CrearClienteRequest request) {
        Long nuevoId = jdbcTemplate.queryForObject("SELECT nextval('cliente_id_seq')", Long.class);
        Cliente cliente = clienteRepository.save(new Cliente(nuevoId, request.nombre().trim(), request.edad(),
                request.email(), request.telefono(), request.direccion()));
        log.info("Cliente {} creado ({})", nuevoId, cliente.getNombre());
        return ClienteInternalDTO.desde(cliente);
    }

    @Transactional
    public ClienteInternalDTO actualizarPerfil(long clienteId, ActualizarPerfilRequest request) {
        Cliente cliente = buscar(clienteId);
        if (request.email() != null) {
            cliente.setEmail(request.email());
        }
        if (request.telefono() != null) {
            cliente.setTelefono(request.telefono());
        }
        if (request.direccion() != null) {
            cliente.setDireccion(request.direccion());
        }
        log.info("Cliente {}: datos de contacto actualizados", clienteId);
        return ClienteInternalDTO.desde(cliente);
    }

    @Transactional
    public ClienteInternalDTO cambiarEstado(long clienteId, String nuevoEstado) {
        Cliente cliente = buscar(clienteId);
        cliente.setEstado(nuevoEstado);
        log.info("Cliente {}: estado cambiado a {}", clienteId, nuevoEstado);
        return ClienteInternalDTO.desde(cliente);
    }

    private Cliente buscar(long clienteId) {
        return clienteRepository.findById(clienteId).orElseThrow(() -> new ClienteNoEncontradoException(clienteId));
    }
}
