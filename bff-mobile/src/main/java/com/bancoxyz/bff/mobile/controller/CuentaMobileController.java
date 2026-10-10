package com.bancoxyz.bff.mobile.controller;

import com.bancoxyz.bff.mobile.client.CuentasServiceClient;
import com.bancoxyz.bff.mobile.client.CuentaInternaDTO;
import com.bancoxyz.bff.mobile.dto.CuentaMobileResumenResponse;
import com.bancoxyz.bff.mobile.exception.AccesoNoAutorizadoException;
import com.bancoxyz.bff.mobile.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/mobile/cuentas")
public class CuentaMobileController {

    private final CuentasServiceClient cuentasServiceClient;

    public CuentaMobileController(CuentasServiceClient cuentasServiceClient) {
        this.cuentasServiceClient = cuentasServiceClient;
    }

    /**
     * Unico endpoint de negocio expuesto por este canal: un "resumen" liviano, no el detalle
     * completo que si ofrece bff-web. Nombre de ruta ({@code /resumen}) deliberadamente
     * explicito para que quede claro, con solo leer la URL, que esta es una vista reducida.
     */
    @GetMapping("/{cuentaId}/resumen")
    public CuentaMobileResumenResponse obtenerResumen(@PathVariable long cuentaId, HttpServletRequest request) {
        long cuentaAutenticada = (long) Optional.ofNullable(request.getAttribute(JwtAuthFilter.ATRIBUTO_CUENTA_ID))
                .orElseThrow(() -> new AccesoNoAutorizadoException("No hay una sesion valida."));
        if (cuentaAutenticada != cuentaId) {
            throw new AccesoNoAutorizadoException("Su sesion no tiene acceso a la cuenta " + cuentaId + ".");
        }
        CuentaInternaDTO cuenta = cuentasServiceClient.obtenerCuenta(cuentaId);
        return CuentaMobileResumenResponse.desde(cuenta);
    }
}
