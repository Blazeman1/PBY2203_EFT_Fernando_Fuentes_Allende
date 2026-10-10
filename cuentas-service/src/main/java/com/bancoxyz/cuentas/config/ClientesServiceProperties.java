package com.bancoxyz.cuentas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/** Como llega este servicio a clientes-service: URL logica (via Eureka) y credenciales OAuth2 propias. */
@ConfigurationProperties(prefix = "clientes-service")
public class ClientesServiceProperties {

    private String baseUrl = "http://clientes-service";

    @NestedConfigurationProperty
    private OAuth2 oauth2 = new OAuth2();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public OAuth2 getOauth2() {
        return oauth2;
    }

    public void setOauth2(OAuth2 oauth2) {
        this.oauth2 = oauth2;
    }

    public static class OAuth2 {
        private String tokenUri = "http://localhost:9000/oauth2/token";
        private String clientId;
        private String clientSecret;
        private String scope = "clientes.access";

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}
