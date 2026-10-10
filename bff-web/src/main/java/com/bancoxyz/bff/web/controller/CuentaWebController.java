package com.bancoxyz.bff.web.controller;

import com.bancoxyz.bff.web.client.ClienteInternoDTO;
import com.bancoxyz.bff.web.client.ClientesServiceClient;
import com.bancoxyz.bff.web.client.CuentasServiceClient;
import com.bancoxyz.bff.web.client.CuentaInternaDTO;
import com.bancoxyz.bff.web.dto.CuentaWebResponse;
import com.bancoxyz.bff.web.dto.MovimientoWebResponse;
import com.bancoxyz.bff.web.exception.AccesoNoAutorizadoException;
import com.bancoxyz.bff.web.exception.ClienteNoEncontradoException;
import com.bancoxyz.bff.web.exception.ServicioNoDisponibleException;
import com.bancoxyz.bff.web.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Endpoints de negocio del canal WEB. Ambos requieren un JWT valido (ver {@link JwtAuthFilter})
 * y, ademas, autorizan que el token solo pueda consultar SU PROPIA cuenta -autenticacion
 * (quien eres) y autorizacion (que puedes ver) son controles distintos y aqui se aplican los dos.
 */
@RestController
@RequestMapping("/api/web/cuentas")
public class CuentaWebController {

    private static final Logger log = LoggerFactory.getLogger(CuentaWebController.class);

    private final CuentasServiceClient cuentasServiceClient;
    private final ClientesServiceClient clientesServiceClient;

    public CuentaWebController(CuentasServiceClient cuentasServiceClient, ClientesServiceClient clientesServiceClient) {
        this.cuentasServiceClient = cuentasServiceClient;
        this.clientesServiceClient = clientesServiceClient;
    }

    /**
     * Vista completa de la cuenta. Composicion de dos microservicios con <b>degradacion
     * controlada</b>: el saldo y los movimientos (cuentas-service) son imprescindibles, pero el
     * nombre y la edad del titular (clientes-service) son un complemento. Si clientes-service no
     * responde, el cliente igual ve su cuenta, con el titular marcado como no disponible, en vez
     * de recibir un error por un dato accesorio.
     */
    @GetMapping("/{cuentaId}")
    public CuentaWebResponse obtenerCuenta(@PathVariable long cuentaId, HttpServletRequest request) {
        verificarPropietario(cuentaId, request);
        CuentaInternaDTO cuenta = cuentasServiceClient.obtenerCuenta(cuentaId);
        ClienteInternoDTO titular;
        try {
            titular = clientesServiceClient.obtenerCliente(cuenta.clienteId());
        } catch (ServicioNoDisponibleException | ClienteNoEncontradoException e) {
            log.warn("Cuenta {}: no se pudo obtener el titular ({}); se responde sin datos personales", cuentaId, e.getMessage());
            titular = null;
        }
        return CuentaWebResponse.desde(cuenta, titular);
    }

    /**
     * Soporte de filtros de consulta (por tipo de movimiento y rango de fechas): el tipo de
     * interfaz compleja (tablas filtrables) que los requerimientos del caso piden para el
     * canal web, y que bff-mobile/bff-atm deliberadamente no ofrecen.
     */
    @GetMapping("/{cuentaId}/movimientos")
    public List<MovimientoWebResponse> listarMovimientos(
            @PathVariable long cuentaId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            HttpServletRequest request) {
        verificarPropietario(cuentaId, request);
        CuentaInternaDTO cuenta = cuentasServiceClient.obtenerCuenta(cuentaId);
        return cuenta.movimientos().stream()
                .filter(m -> tipo == null || tipo.equalsIgnoreCase(m.tipoMovimiento()))
                .filter(m -> desde == null || !m.fecha().isBefore(desde))
                .filter(m -> hasta == null || !m.fecha().isAfter(hasta))
                .map(MovimientoWebResponse::desde)
                .toList();
    }

    private void verificarPropietario(long cuentaId, HttpServletRequest request) {
        long cuentaAutenticada = (long) Optional.ofNullable(request.getAttribute(JwtAuthFilter.ATRIBUTO_CUENTA_ID))
                .orElseThrow(() -> new AccesoNoAutorizadoException("No hay una sesion valida."));
        if (cuentaAutenticada != cuentaId) {
            throw new AccesoNoAutorizadoException("Su sesion no tiene acceso a la cuenta " + cuentaId + ".");
        }
    }
}
