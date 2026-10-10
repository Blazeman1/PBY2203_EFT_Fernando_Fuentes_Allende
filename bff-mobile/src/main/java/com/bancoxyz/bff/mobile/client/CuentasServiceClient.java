package com.bancoxyz.bff.mobile.client;

import com.bancoxyz.bff.mobile.config.CuentasServiceProperties;
import com.bancoxyz.bff.mobile.exception.CuentasServiceNoDisponibleException;
import com.bancoxyz.bff.mobile.exception.CuentaNoEncontradaException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Unico punto de acceso de bff-mobile hacia cuentas-service. Agrega el encabezado
 * {@code Authorization: Bearer <token>} de forma centralizada (token OAuth2.0
 * client_credentials obtenido via {@link CuentasServiceTokenProvider}).
 */
@Component
public class CuentasServiceClient {

    private final RestTemplate restTemplate;
    private final CuentasServiceProperties propiedades;
    private final CuentasServiceTokenProvider tokenProvider;

    public CuentasServiceClient(RestTemplate restTemplate, CuentasServiceProperties propiedades,
                              CuentasServiceTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.propiedades = propiedades;
        this.tokenProvider = tokenProvider;
    }

    @CircuitBreaker(name = "cuentasService", fallbackMethod = "obtenerCuentaFallback")
    public CuentaInternaDTO obtenerCuenta(long cuentaId) {
        try {
            var respuesta = restTemplate.exchange(
                    propiedades.getBaseUrl() + "/internal/cuentas/{cuentaId}",
                    HttpMethod.GET,
                    new HttpEntity<>(cabecerasInternas()),
                    CuentaInternaDTO.class,
                    cuentaId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new CuentaNoEncontradaException(cuentaId);
        }
    }

    private CuentaInternaDTO obtenerCuentaFallback(long cuentaId, Throwable t) {
        if (t instanceof CuentaNoEncontradaException) {
            throw (CuentaNoEncontradaException) t;
        }
        throw new CuentasServiceNoDisponibleException(
                "cuentas-service no disponible en este momento, intente mas tarde.", t);
    }

    private HttpHeaders cabecerasInternas() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return headers;
    }
}
