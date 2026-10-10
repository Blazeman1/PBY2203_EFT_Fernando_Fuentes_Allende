package com.bancoxyz.cuentas.client;

import com.bancoxyz.cuentas.config.ClientesServiceProperties;
import com.bancoxyz.cuentas.exception.ClienteNoEncontradoException;
import com.bancoxyz.cuentas.exception.ClientesServiceNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Unica dependencia sincronica de cuentas-service: antes de abrir una cuenta se verifica en
 * clientes-service que el cliente exista. Protegida con circuit breaker (Resilience4j).
 *
 * <p><b>Comportamiento alternativo ante falla:</b> si clientes-service no responde, la apertura
 * se rechaza de inmediato con un 503 controlado en vez de abrir una cuenta para un cliente que
 * no se pudo verificar. Es un "fallo cerrado" deliberado: en este caso es preferible no operar
 * que operar a ciegas. El resto de las operaciones (consultas, debitos, creditos, saga) no
 * depende de clientes-service y sigue funcionando con normalidad.</p>
 */
@Component
public class ClientesServiceClient {

    private final RestTemplate restTemplate;
    private final ClientesServiceProperties propiedades;
    private final ServiceTokenProvider tokenProvider;

    public ClientesServiceClient(RestTemplate restTemplate, ClientesServiceProperties propiedades,
                                 ServiceTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.propiedades = propiedades;
        this.tokenProvider = tokenProvider;
    }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "obtenerClienteFallback")
    public ClienteDTO obtenerCliente(long clienteId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenProvider.obtenerToken());
            var respuesta = restTemplate.exchange(
                    propiedades.getBaseUrl() + "/internal/clientes/{clienteId}",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    ClienteDTO.class,
                    clienteId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ClienteNoEncontradoException(clienteId);
        }
    }

    private ClienteDTO obtenerClienteFallback(long clienteId, Throwable t) {
        if (t instanceof ClienteNoEncontradoException) {
            throw (ClienteNoEncontradoException) t;
        }
        throw new ClientesServiceNoDisponibleException(
                "No fue posible verificar al cliente: clientes-service no esta disponible en este momento.", t);
    }
}
