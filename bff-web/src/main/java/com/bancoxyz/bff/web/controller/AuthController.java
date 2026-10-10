package com.bancoxyz.bff.web.controller;

import com.bancoxyz.bff.web.client.ClienteInternoDTO;
import com.bancoxyz.bff.web.client.ClientesServiceClient;
import com.bancoxyz.bff.web.client.CuentaInternaDTO;
import com.bancoxyz.bff.web.client.CuentasServiceClient;
import com.bancoxyz.bff.web.dto.LoginResponse;
import com.bancoxyz.bff.web.dto.LoginWebRequest;
import com.bancoxyz.bff.web.exception.AutenticacionException;
import com.bancoxyz.bff.web.exception.ClienteNoEncontradoException;
import com.bancoxyz.bff.web.exception.CuentaNoEncontradaException;
import com.bancoxyz.bff.web.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/auth")
public class AuthController {

    private final CuentasServiceClient cuentasServiceClient;
    private final ClientesServiceClient clientesServiceClient;
    private final JwtService jwtService;

    public AuthController(CuentasServiceClient cuentasServiceClient, ClientesServiceClient clientesServiceClient,
                          JwtService jwtService) {
        this.cuentasServiceClient = cuentasServiceClient;
        this.clientesServiceClient = clientesServiceClient;
        this.jwtService = jwtService;
    }

    /**
     * Login del canal web: numero de cuenta + nombre del titular. Aqui se ve el BFF como
     * COMPOSITOR: la cuenta vive en cuentas-service y el nombre del titular en clientes-service;
     * el frontend hace una sola llamada y este BFF consulta ambos.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginWebRequest request) {
        // Cualquier fallo de autenticacion (cuenta inexistente, cerrada, o nombre que no calza)
        // se reporta con el MISMO mensaje generico, para no revelar si un numero de cuenta existe.
        CuentaInternaDTO cuenta;
        ClienteInternoDTO titular;
        try {
            cuenta = cuentasServiceClient.obtenerCuenta(request.cuentaId());
            titular = clientesServiceClient.obtenerCliente(cuenta.clienteId());
        } catch (CuentaNoEncontradaException | ClienteNoEncontradoException e) {
            throw new AutenticacionException("Credenciales invalidas.");
        }
        if (!"ACTIVA".equals(cuenta.estado()) || !"ACTIVO".equals(titular.estado())
                || !titular.nombre().trim().equalsIgnoreCase(request.nombre().trim())) {
            throw new AutenticacionException("Credenciales invalidas.");
        }
        String token = jwtService.generarToken(cuenta.cuentaId(), titular.nombre());
        return new LoginResponse(token, jwtService.validezEnSegundos(), cuenta.cuentaId(), titular.nombre());
    }
}
