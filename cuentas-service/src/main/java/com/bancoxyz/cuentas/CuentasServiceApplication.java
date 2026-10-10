package com.bancoxyz.cuentas;

import com.bancoxyz.cuentas.config.ClientesServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Microservicio de <b>Gestion de Cuentas</b> del Banco XYZ (EFT, Parte 3).
 *
 * <p>Nace de dividir el antiguo {@code core-service} (un unico "backend generalizado" con los
 * datos en memoria) en tres microservicios con responsabilidades y bases de datos propias:
 * {@code clientes-service} (quien es el titular), este servicio (que cuentas tiene y cuanto
 * saldo hay en cada una) y {@code pagos-service} (que operaciones de dinero se solicitaron y en
 * que estado estan). Este servicio es la <b>fuente unica de verdad del saldo</b>: ningun otro
 * componente modifica un saldo sin pasar por aqui, ya sea por REST (retiros, depositos, pagos)
 * o por eventos Kafka (los pasos de debito, credito y compensacion de la saga de
 * transferencias).</p>
 *
 * <p>No es consumido por ningun frontend: solo por los tres BFF y por pagos-service,
 * autenticados como clientes OAuth2.0 (flujo {@code client_credentials}) ante auth-server.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ClientesServiceProperties.class)
public class CuentasServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuentasServiceApplication.class, args);
    }

    /** RestTemplate balanceado: resuelve "http://clientes-service" contra las instancias registradas en Eureka. */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
