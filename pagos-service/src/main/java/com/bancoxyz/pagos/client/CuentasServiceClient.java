package com.bancoxyz.pagos.client;

import com.bancoxyz.pagos.config.CuentasServiceProperties;
import com.bancoxyz.pagos.exception.CuentaNoEncontradaException;
import com.bancoxyz.pagos.exception.CuentasServiceNoDisponibleException;
import com.bancoxyz.pagos.exception.OperacionNoPermitidaException;
import com.bancoxyz.pagos.exception.SaldoInsuficienteException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Unico punto de acceso sincronico de pagos-service hacia cuentas-service, protegido con dos
 * patrones de Resilience4j combinados:
 *
 * <ul>
 *   <li><b>Retry</b> (capa externa): ante una falla transitoria (timeout, instancia que se acaba
 *       de caer, error 5xx) reintenta hasta 3 veces. Como la llamada esta balanceada por Eureka,
 *       un reintento puede caer en OTRA replica sana de cuentas-service.</li>
 *   <li><b>Circuit Breaker</b> (capa interna): si las fallas persisten, abre el circuito y deja
 *       de enviar trafico a un servicio que no responde, en vez de acumular hilos esperando.</li>
 * </ul>
 *
 * <p><b>Por que es seguro reintentar una operacion de dinero.</b> Cada llamada lleva un
 * {@code idOperacion} (el id del pago). Si un debito se aplico pero la respuesta se perdio por
 * un timeout, el reintento llega con la misma clave y cuentas-service lo reconoce como ya
 * aplicado: devuelve el estado actual sin debitar por segunda vez.</p>
 *
 * <p><b>Comportamiento alternativo ante falla:</b> agotados los reintentos (o con el circuito
 * abierto) se lanza {@link CuentasServiceNoDisponibleException}; el pago queda registrado como
 * FALLIDA con su motivo y el llamante recibe un 503 controlado. Los errores de negocio (cuenta
 * inexistente, saldo insuficiente, cuenta cerrada) NO se reintentan ni cuentan como falla del
 * circuito: son respuestas correctas de un servicio sano.</p>
 */
@Component
public class CuentasServiceClient {

    private final RestTemplate restTemplate;
    private final CuentasServiceProperties propiedades;
    private final ServiceTokenProvider tokenProvider;

    public CuentasServiceClient(RestTemplate restTemplate, CuentasServiceProperties propiedades,
                                ServiceTokenProvider tokenProvider) {
        this.restTemplate = restTemplate;
        this.propiedades = propiedades;
        this.tokenProvider = tokenProvider;
    }

    @Retry(name = "cuentasService", fallbackMethod = "moverSaldoFallback")
    @CircuitBreaker(name = "cuentasService")
    public CuentaDTO debitar(long cuentaId, double monto, String tipoMovimiento, String descripcion, String idOperacion) {
        return moverSaldo("debito", cuentaId, monto, tipoMovimiento, descripcion, idOperacion);
    }

    @Retry(name = "cuentasService", fallbackMethod = "moverSaldoFallback")
    @CircuitBreaker(name = "cuentasService")
    public CuentaDTO acreditar(long cuentaId, double monto, String tipoMovimiento, String descripcion, String idOperacion) {
        return moverSaldo("credito", cuentaId, monto, tipoMovimiento, descripcion, idOperacion);
    }

    private CuentaDTO moverSaldo(String accion, long cuentaId, double monto, String tipoMovimiento,
                                 String descripcion, String idOperacion) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("monto", monto);
        cuerpo.put("tipoMovimiento", tipoMovimiento);
        cuerpo.put("descripcion", descripcion);
        cuerpo.put("idOperacion", idOperacion);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenProvider.obtenerToken());
        try {
            var respuesta = restTemplate.exchange(
                    propiedades.getBaseUrl() + "/internal/cuentas/{cuentaId}/" + accion,
                    HttpMethod.POST,
                    new HttpEntity<>(cuerpo, headers),
                    CuentaDTO.class,
                    cuentaId);
            return respuesta.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new CuentaNoEncontradaException(cuentaId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new SaldoInsuficienteException("Saldo insuficiente en la cuenta " + cuentaId + ".");
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new OperacionNoPermitidaException("La cuenta " + cuentaId + " no admite la operacion (cuenta cerrada).");
        }
    }

    /** Fallback comun a debitar/acreditar (misma lista de argumentos mas la causa). */
    private CuentaDTO moverSaldoFallback(long cuentaId, double monto, String tipoMovimiento, String descripcion,
                                         String idOperacion, Throwable t) {
        if (t instanceof CuentaNoEncontradaException || t instanceof SaldoInsuficienteException
                || t instanceof OperacionNoPermitidaException) {
            throw (RuntimeException) t;
        }
        throw new CuentasServiceNoDisponibleException(
                "cuentas-service no disponible en este momento; el pago no fue aplicado, intente mas tarde.", t);
    }
}
