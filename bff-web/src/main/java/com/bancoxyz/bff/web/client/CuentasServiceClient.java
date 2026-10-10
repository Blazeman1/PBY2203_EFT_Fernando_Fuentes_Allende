package com.bancoxyz.bff.web.client;

import com.bancoxyz.bff.web.config.CuentasServiceProperties;
import com.bancoxyz.bff.web.exception.CuentasServiceNoDisponibleException;
import com.bancoxyz.bff.web.exception.CuentaNoEncontradaException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

/**
 * Unico punto de acceso de bff-web hacia cuentas-service. Ningun
 * controlador de este modulo llama a {@code RestTemplate} directamente: todos pasan por aqui,
 * que es quien agrega el encabezado {@code Authorization: Bearer <token>} de forma centralizada
 * (token OAuth2.0 client_credentials obtenido via {@link CuentasServiceTokenProvider}).
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
        // Si auth-server no responde, tokenProvider.obtenerToken() lanza una excepcion que se
        // propaga tal cual al metodo @CircuitBreaker que llamo a este cliente: un token no
        // obtenido y un cuentas-service caido producen, a proposito, el mismo efecto observable
        // (fallback de "cuentas-service no disponible"), sin necesidad de un circuit breaker aparte.
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return headers;
    }
}
