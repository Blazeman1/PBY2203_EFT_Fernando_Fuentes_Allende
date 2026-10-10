package com.bancoxyz.bff.atm.controller;

import com.bancoxyz.bff.atm.client.CuentasServiceClient;
import com.bancoxyz.bff.atm.client.CuentaInternaDTO;
import com.bancoxyz.bff.atm.dto.SesionRequest;
import com.bancoxyz.bff.atm.dto.SesionResponse;
import com.bancoxyz.bff.atm.exception.AutenticacionException;
import com.bancoxyz.bff.atm.exception.CuentaNoEncontradaException;
import com.bancoxyz.bff.atm.security.PinGenerator;
import com.bancoxyz.bff.atm.security.SesionAtmService;
import com.bancoxyz.bff.atm.security.TarjetaGenerator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/atm")
public class SesionController {

    private final CuentasServiceClient cuentasServiceClient;
    private final SesionAtmService sesionAtmService;

    public SesionController(CuentasServiceClient cuentasServiceClient, SesionAtmService sesionAtmService) {
        this.cuentasServiceClient = cuentasServiceClient;
        this.sesionAtmService = sesionAtmService;
    }

    /** Valida tarjeta + PIN (dos factores) y, si son correctos, abre una sesion de 2 minutos. */
    @PostMapping("/sesion")
    public SesionResponse iniciarSesion(@Valid @RequestBody SesionRequest request) {
        long cuentaId = TarjetaGenerator.extraerCuentaId(request.numeroTarjeta())
                .orElseThrow(() -> new AutenticacionException("Tarjeta invalida."));

        CuentaInternaDTO cuenta;
        try {
            cuenta = cuentasServiceClient.obtenerCuenta(cuentaId);
        } catch (CuentaNoEncontradaException e) {
            throw new AutenticacionException("Tarjeta o PIN incorrectos.");
        }
        if (!PinGenerator.generar(cuenta.cuentaId()).equals(request.pin().trim())) {
            throw new AutenticacionException("Tarjeta o PIN incorrectos.");
        }

        String token = sesionAtmService.crear(cuenta.cuentaId());
        return new SesionResponse(token, sesionAtmService.validezEnSegundos(), cuenta.cuentaId());
    }
}
