package com.bancoxyz.pagos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * pagos-service como <b>OAuth2 Resource Server</b>: exige un JWT emitido por auth-server con el
 * scope {@code pagos.access}. Lo poseen bff-web (transferencias y pagos de servicios) y bff-atm
 * (depositos); bff-mobile, que es un canal de solo consulta, no.
 */
@Configuration
public class ResourceServerConfig {

    private static final String SCOPE_REQUERIDO = "pagos.access";

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
