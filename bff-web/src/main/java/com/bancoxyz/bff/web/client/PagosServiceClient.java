package com.bancoxyz.bff.web.client;

import com.bancoxyz.bff.web.exception.ServicioNoDisponibleException;
import com.bancoxyz.bff.web.transferencia.dto.TransferenciaEstadoDTO;
import com.bancoxyz.bff.web.transferencia.exception.TransferenciaNoEncontradaException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceso de bff-web a pagos-service: transferencias, pagos de servicios e historial de pagos.
 *
 * <p>Este BFF ya no publica eventos en Kafka por su cuenta: solicita la transferencia a
 * pagos-service por REST y es ese microservicio -dueno del registro de pagos- quien inicia la
 * saga. El BFF queda como lo que debe ser: una capa de adaptacion al canal, sin logica de
 * mensajeria ni conocimiento de los topicos.</p>
 *
 * <p>Circuit breaker propio ({@code pagosService}). Comportamiento alternativo ante falla: un
 * 503 controlado con un mensaje claro para el canal, en vez de una traza de error cruda.</p>
 */
@Component
public class PagosServiceClient {

    private static final String MENSAJE_NO_DISPONIBLE = "pagos-service no disponible en este momento, intente mas tarde.";

    private final RestTemplate restTemplate;
    private final CuentasServiceTokenProvider tokenProvider;
    private final String baseUrl;

    public PagosServiceClient(RestTemplate restTemplate, CuentasServiceTokenProvider tokenProvider,
                              @Value("${pagos-service.base-url:http://pagos-service}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "solicitarTransferenciaFallback")
    public PagoInternoDTO solicitarTransferencia(long cuentaOrigen, long cuentaDestino, double monto) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("cuentaOrigen", cuentaOrigen);
        cuerpo.put("cuentaDestino", cuentaDestino);
        cuerpo.put("monto", monto);
        return restTemplate.exchange(baseUrl + "/internal/pagos/transferencias", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabecerasInternas()), PagoInternoDTO.class).getBody();
    }

    private PagoInternoDTO solicitarTransferenciaFallback(long cuentaOrigen, long cuentaDestino, double monto, Throwable t) {
        throw new ServicioNoDisponibleException(MENSAJE_NO_DISPONIBLE, t);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "obtenerEstadoFallback")
    public TransferenciaEstadoDTO obtenerEstadoTransferencia(String transferenciaId) {
        try {
            return restTemplate.exchange(baseUrl + "/internal/transferencias/{id}", HttpMethod.GET,
                    new HttpEntity<>(cabecerasInternas()), TransferenciaEstadoDTO.class, transferenciaId).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new TransferenciaNoEncontradaException(transferenciaId);
        }
    }

    private TransferenciaEstadoDTO obtenerEstadoFallback(String transferenciaId, Throwable t) {
        if (t instanceof TransferenciaNoEncontradaException) {
            throw (TransferenciaNoEncontradaException) t;
        }
        throw new ServicioNoDisponibleException(MENSAJE_NO_DISPONIBLE, t);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "pagarServicioFallback")
    public PagoInternoDTO pagarServicio(long cuentaOrigen, double monto, String comercio) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("cuentaOrigen", cuentaOrigen);
        cuerpo.put("monto", monto);
        cuerpo.put("comercio", comercio);
        return restTemplate.exchange(baseUrl + "/internal/pagos/servicios", HttpMethod.POST,
                new HttpEntity<>(cuerpo, cabecerasInternas()), PagoInternoDTO.class).getBody();
    }

    private PagoInternoDTO pagarServicioFallback(long cuentaOrigen, double monto, String comercio, Throwable t) {
        throw new ServicioNoDisponibleException(MENSAJE_NO_DISPONIBLE, t);
    }

    @CircuitBreaker(name = "pagosService", fallbackMethod = "historialFallback")
    public List<PagoInternoDTO> historial(long cuentaId) {
        return restTemplate.exchange(baseUrl + "/internal/pagos?cuentaId={cuentaId}", HttpMethod.GET,
                new HttpEntity<>(cabecerasInternas()), new ParameterizedTypeReference<List<PagoInternoDTO>>() {
                }, cuentaId).getBody();
    }

    private List<PagoInternoDTO> historialFallback(long cuentaId, Throwable t) {
        throw new ServicioNoDisponibleException(MENSAJE_NO_DISPONIBLE, t);
    }

    private HttpHeaders cabecerasInternas() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.obtenerToken());
        return headers;
    }
}
