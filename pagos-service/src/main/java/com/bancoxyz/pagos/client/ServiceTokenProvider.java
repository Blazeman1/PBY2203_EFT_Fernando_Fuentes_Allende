package com.bancoxyz.pagos.client;

import com.bancoxyz.pagos.config.CuentasServiceProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Obtiene y cachea el access token OAuth2.0 ({@code client_credentials}) con el que
 * pagos-service se identifica ante cuentas-service. Mismo mecanismo que usan los BFF: tambien
 * entre microservicios la confianza se establece con un token emitido por auth-server, no por el
 * solo hecho de estar en la misma red.
 *
 * <p>Usa un {@link RestTemplate} propio y deliberadamente NO balanceado: auth-server es una
 * direccion fija conocida por configuracion, no un servicio que se resuelva por Eureka.</p>
 */
@Component
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    private static final long SEGUNDOS_MARGEN_RENOVACION = 10;

    private final RestTemplate restTemplateAuthServer = new RestTemplate();
    private final CuentasServiceProperties propiedades;

    private volatile String tokenCacheado;
    private volatile Instant expiraEn = Instant.MIN;

    public ServiceTokenProvider(CuentasServiceProperties propiedades) {
        this.propiedades = propiedades;
    }

    public synchronized String obtenerToken() {
        if (tokenCacheado != null && Instant.now().isBefore(expiraEn)) {
            return tokenCacheado;
        }
        RespuestaToken respuesta = solicitarNuevoToken();
        this.tokenCacheado = respuesta.accessToken;
        this.expiraEn = Instant.now().plusSeconds(Math.max(0, respuesta.expiresIn - SEGUNDOS_MARGEN_RENOVACION));
        return tokenCacheado;
    }

    private RespuestaToken solicitarNuevoToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(propiedades.getOauth2().getClientId(), propiedades.getOauth2().getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        // Imprescindible: Spring Authorization Server NO concede automaticamente los scopes
        // registrados del cliente si la peticion omite "scope" (el token sale sin scope alguno y
        // el Resource Server responde 403 insufficient_scope).
        body.add("scope", propiedades.getOauth2().getScope());

        var respuesta = restTemplateAuthServer.postForEntity(
                propiedades.getOauth2().getTokenUri(),
                new HttpEntity<>(body, headers),
                RespuestaToken.class);

        RespuestaToken cuerpo = respuesta.getBody();
        if (cuerpo == null || cuerpo.accessToken == null) {
            throw new IllegalStateException("auth-server no devolvio un access_token valido.");
        }
        log.debug("Nuevo access token obtenido de auth-server (expira en {}s)", cuerpo.expiresIn);
        return cuerpo;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RespuestaToken {
        @JsonProperty("access_token")
        String accessToken;
        @JsonProperty("expires_in")
        long expiresIn;
    }
}
