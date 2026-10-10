package com.bancoxyz.bff.atm.client;

import com.bancoxyz.bff.atm.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Acceso de bff-atm a pagos-service, solo para depositos. Circuit breaker propio
 * ({@code pagosService}), independiente del de cuentas-service: si pagos-service cae, el cajero
 * deja de aceptar depositos pero sigue entregando saldo y retiros con normalidad.
 */
@Component
public class PagosServiceClient {

    private final RestTemplate restTemplate;
    private final CuentasServiceTokenProvider tokenProvider;
    private final String baseUrl;

    public PagosServiceClient(RestTemplate restTemplate, CuentasServiceTokenProvider tokenProvider,
                              @Value("${pagos-service.base-url:http://pagos-service}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "depositarFallback")
    public PagoInternoDTO depositar(long cuentaDestino, double monto) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("cuentaDestino", cuentaDestino);
        cuerpo.put("monto", monto);
        cuerpo.put("glosa", "Deposito por cajero automatico");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return restTemplate.exchange(baseUrl + "/internal/pagos/depositos", HttpMethod.POST,
                new HttpEntity<>(cuerpo, headers), PagoInternoDTO.class).getBody();
    }

    private PagoInternoDTO depositarFallback(long cuentaDestino, double monto, Throwable t) {
        throw new ServicioNoDisponibleException(
                "El servicio de depositos no esta disponible en este momento; su dinero no fue ingresado.", t);
    }
}
