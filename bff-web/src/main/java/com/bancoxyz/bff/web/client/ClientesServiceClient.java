package com.bancoxyz.bff.web.client;

import com.bancoxyz.bff.web.exception.ClienteNoEncontradoException;
import com.bancoxyz.bff.web.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Acceso de bff-web a clientes-service (perfil del titular). Solo el canal web lo necesita:
 * bff-mobile y bff-atm no muestran datos personales y su token ni siquiera incluye el scope
 * {@code clientes.access}.
 *
 * <p>Circuit breaker propio ({@code clientesService}), independiente del de cuentas-service: una
 * caida del servicio de clientes no debe contar como falla del servicio de cuentas, ni al reves.</p>
 */
@Component
public class ClientesServiceClient {

    private final RestTemplate restTemplate;
    private final CuentasServiceTokenProvider tokenProvider;
    private final String baseUrl;

    public ClientesServiceClient(RestTemplate restTemplate, CuentasServiceTokenProvider tokenProvider,
                                 @Value("${clientes-service.base-url:http://clientes-service}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "obtenerClienteFallback")
    public ClienteInternoDTO obtenerCliente(long clienteId) {
        try {
            var respuesta = restTemplate.exchange(
                    baseUrl + "/internal/clientes/{clienteId}",
                    HttpMethod.GET,
                    new HttpEntity<>(cabecerasInternas()),
                    ClienteInternoDTO.class,
                    clienteId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ClienteNoEncontradoException(clienteId);
        }
    }

    private ClienteInternoDTO obtenerClienteFallback(long clienteId, Throwable t) {
        if (t instanceof ClienteNoEncontradoException) {
            throw (ClienteNoEncontradoException) t;
        }
        throw new ServicioNoDisponibleException("clientes-service no disponible en este momento, intente mas tarde.", t);
    }

    @CircuitBreaker(name = "clientesService", fallbackMethod = "actualizarContactoFallback")
    public ClienteInternoDTO actualizarContacto(long clienteId, String email, String telefono, String direccion) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("email", email);
        cuerpo.put("telefono", telefono);
        cuerpo.put("direccion", direccion);
        try {
            var respuesta = restTemplate.exchange(
                    baseUrl + "/internal/clientes/{clienteId}",
                    HttpMethod.PUT,
                    new HttpEntity<>(cuerpo, cabecerasInternas()),
                    ClienteInternoDTO.class,
                    clienteId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ClienteNoEncontradoException(clienteId);
        }
    }

    private ClienteInternoDTO actualizarContactoFallback(long clienteId, String email, String telefono, String direccion, Throwable t) {
        if (t instanceof ClienteNoEncontradoException) {
            throw (ClienteNoEncontradoException) t;
        }
        throw new ServicioNoDisponibleException("clientes-service no disponible en este momento, intente mas tarde.", t);
    }

    private HttpHeaders cabecerasInternas() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return headers;
    }
}
