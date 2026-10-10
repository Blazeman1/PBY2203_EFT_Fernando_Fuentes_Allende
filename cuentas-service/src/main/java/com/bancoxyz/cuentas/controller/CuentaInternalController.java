package com.bancoxyz.cuentas.controller;

import com.bancoxyz.cuentas.client.ClienteDTO;
import com.bancoxyz.cuentas.client.ClientesServiceClient;
import com.bancoxyz.cuentas.dto.ActualizarSaldoRequest;
import com.bancoxyz.cuentas.dto.AperturaCuentaRequest;
import com.bancoxyz.cuentas.dto.CuentaInternalDTO;
import com.bancoxyz.cuentas.dto.MantenimientoCuentaRequest;
import com.bancoxyz.cuentas.exception.OperacionNoPermitidaException;
import com.bancoxyz.cuentas.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API interna de Gestion de Cuentas (apertura, cierre, mantenimiento, consulta y movimientos de
 * saldo). Protegida por {@link com.bancoxyz.cuentas.config.ResourceServerConfig}: exige un JWT
 * de auth-server con scope {@code cuentas.access}.
 *
 * <p>Las acciones se modelan como {@code POST /{id}/accion} y no como {@code PATCH}: el
 * {@code RestTemplate} por defecto (basado en {@code HttpURLConnection} de la JDK) no soporta
 * PATCH, y usar POST evita agregar otro cliente HTTP solo por ese verbo.</p>
 */
@RestController
@RequestMapping("/internal/cuentas")
public class CuentaInternalController {

    private final CuentaService cuentaService;
    private final ClientesServiceClient clientesServiceClient;

    public CuentaInternalController(CuentaService cuentaService, ClientesServiceClient clientesServiceClient) {
        this.cuentaService = cuentaService;
        this.clientesServiceClient = clientesServiceClient;
    }

    /** Lista cuentas (sin historial). Con {@code ?clienteId=} filtra las de un cliente. */
    @GetMapping
    public List<CuentaInternalDTO> listar(@RequestParam(required = false) Long clienteId) {
        return cuentaService.listar(clienteId);
    }

    @GetMapping("/{cuentaId}")
    public CuentaInternalDTO obtener(@PathVariable long cuentaId) {
        return cuentaService.obtener(cuentaId);
    }

    /** Apertura de cuenta: valida primero, contra clientes-service, que el cliente exista y este activo. */
    @PostMapping
    public ResponseEntity<CuentaInternalDTO> abrir(@Valid @RequestBody AperturaCuentaRequest request) {
        ClienteDTO cliente = clientesServiceClient.obtenerCliente(request.clienteId());
        if (cliente == null || !cliente.estaActivo()) {
            throw new OperacionNoPermitidaException("El cliente " + request.clienteId()
                    + " no esta activo: no se le puede abrir una cuenta.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cuentaService.abrir(request));
    }

    /** Mantenimiento: cambio de tipo de producto de la cuenta. */
    @PutMapping("/{cuentaId}")
    public CuentaInternalDTO actualizar(@PathVariable long cuentaId, @Valid @RequestBody MantenimientoCuentaRequest request) {
        return cuentaService.actualizar(cuentaId, request);
    }

    @PostMapping("/{cuentaId}/cierre")
    public CuentaInternalDTO cerrar(@PathVariable long cuentaId) {
        return cuentaService.cerrar(cuentaId);
    }

    /**
     * Debita saldo (retiro por cajero, pago de servicio). La regla "saldo suficiente" se valida
     * aqui, en la fuente unica de verdad del saldo, y no en quien llama: asi ningun BFF ni otro
     * microservicio puede dejar una cuenta en negativo saltandose la regla.
     */
    @PostMapping("/{cuentaId}/debito")
    public CuentaInternalDTO debitar(@PathVariable long cuentaId, @Valid @RequestBody ActualizarSaldoRequest request) {
        return cuentaService.debitar(cuentaId, request);
    }

    /** Acredita saldo (deposito). */
    @PostMapping("/{cuentaId}/credito")
    public CuentaInternalDTO acreditar(@PathVariable long cuentaId, @Valid @RequestBody ActualizarSaldoRequest request) {
        return cuentaService.acreditar(cuentaId, request);
    }
}
