package com.bancoxyz.bff.atm.client;

import com.bancoxyz.bff.atm.config.CuentasServiceProperties;
import com.bancoxyz.bff.atm.exception.CuentasServiceNoDisponibleException;
import com.bancoxyz.bff.atm.exception.CuentaNoEncontradaException;
import com.bancoxyz.bff.atm.exception.OperacionNoPermitidaException;
import com.bancoxyz.bff.atm.exception.SaldoInsuficienteException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Unico punto de acceso de bff-atm hacia cuentas-service. Agrega el encabezado
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

    /**
     * Confirma un retiro debitando el saldo en cuentas-service (fuente unica de verdad
     * del dinero disponible). La validacion real de "saldo suficiente" ocurre en cuentas-service,
     * no aqui: este BFF solo aplica sus propias reglas de canal (limite maximo por operacion,
     * ver {@code LimiteRetiroExcedidoException}) antes de reenviar la solicitud.
     */
    @CircuitBreaker(name = "cuentasService", fallbackMethod = "debitarSaldoFallback")
    public CuentaInternaDTO debitarSaldo(long cuentaId, double monto) {
        try {
            var respuesta = restTemplate.exchange(
                    propiedades.getBaseUrl() + "/internal/cuentas/{cuentaId}/debito",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("monto", monto), cabecerasInternas()),
                    CuentaInternaDTO.class,
                    cuentaId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new CuentaNoEncontradaException(cuentaId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar el retiro solicitado.");
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new OperacionNoPermitidaException("La cuenta no admite retiros (cuenta cerrada).");
        }
    }

    private CuentaInternaDTO debitarSaldoFallback(long cuentaId, double monto, Throwable t) {
        if (t instanceof CuentaNoEncontradaException || t instanceof SaldoInsuficienteException
                || t instanceof OperacionNoPermitidaException) {
            throw (RuntimeException) t;
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
