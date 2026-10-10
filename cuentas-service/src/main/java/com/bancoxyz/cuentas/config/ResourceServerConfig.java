package com.bancoxyz.cuentas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * cuentas-service como <b>OAuth2 Resource Server</b>: toda peticion debe traer un JWT emitido por
 * auth-server (firma, expiracion y emisor se validan automaticamente a partir de
 * {@code issuer-uri}) y, ademas, el scope {@code cuentas.access}.
 *
 * <p>Seguridad distribuida con minimo privilegio: cada microservicio exige SU propio scope
 * ({@code cuentas.access}, {@code clientes.access}, {@code pagos.access}) y auth-server otorga a
 * cada cliente solo los que necesita. bff-mobile, por ejemplo, puede leer cuentas pero su token
 * no sirve para llamar a pagos-service ni a clientes-service.</p>
 */
@Configuration
public class ResourceServerConfig {

    private static final String SCOPE_REQUERIDO = "cuentas.access";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        // Lo consulta el healthcheck de docker-compose, que no tiene como obtener un JWT.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().hasAuthority("SCOPE_" + SCOPE_REQUERIDO))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // Solo trafico servidor-a-servidor con Bearer token: no hay cookies de sesion ni superficie CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
        scopesConverter.setAuthorityPrefix("SCOPE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopesConverter);
        return converter;
    }
}
