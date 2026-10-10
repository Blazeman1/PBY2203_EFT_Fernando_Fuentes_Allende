package com.bancoxyz.bff.mobile.controller;

import com.bancoxyz.bff.mobile.client.CuentasServiceClient;
import com.bancoxyz.bff.mobile.client.CuentaInternaDTO;
import com.bancoxyz.bff.mobile.dto.LoginMobileRequest;
import com.bancoxyz.bff.mobile.dto.LoginResponse;
import com.bancoxyz.bff.mobile.exception.AutenticacionException;
import com.bancoxyz.bff.mobile.exception.CuentaNoEncontradaException;
import com.bancoxyz.bff.mobile.security.JwtService;
import com.bancoxyz.bff.mobile.security.PinGenerator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class AuthController {

    private final CuentasServiceClient cuentasServiceClient;
    private final JwtService jwtService;

    public AuthController(CuentasServiceClient cuentasServiceClient, JwtService jwtService) {
        this.cuentasServiceClient = cuentasServiceClient;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginMobileRequest request) {
        CuentaInternaDTO cuenta;
        try {
            cuenta = cuentasServiceClient.obtenerCuenta(request.cuentaId());
        } catch (CuentaNoEncontradaException e) {
            throw new AutenticacionException("Credenciales invalidas.");
        }
        if (!PinGenerator.generar(cuenta.cuentaId()).equals(request.pin().trim())) {
            throw new AutenticacionException("Credenciales invalidas.");
        }
        String token = jwtService.generarToken(cuenta.cuentaId());
        return new LoginResponse(token, jwtService.validezEnSegundos());
    }
}
