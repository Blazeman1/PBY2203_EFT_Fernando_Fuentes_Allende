package com.bancoxyz.pagos;

import com.bancoxyz.pagos.config.CuentasServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Microservicio de <b>Procesamiento de Pagos</b> del Banco XYZ (EFT, Parte 3): transferencias
 * entre cuentas, depositos y pagos de servicios.
 *
 * <p>Es dueno del REGISTRO de cada operacion (que se pidio, cuando y en que estado quedo), pero
 * no de los saldos: para mover dinero siempre recurre a cuentas-service, por dos vias segun la
 * naturaleza de la operacion:</p>
 * <ul>
 *   <li><b>Transferencias: asincronas, por eventos Kafka.</b> Tocan dos cuentas y se modelan
 *       como una saga: este servicio publica la solicitud y se entera del desenlace consumiendo
 *       el evento de resultado.</li>
 *   <li><b>Depositos y pagos de servicios: sincronicos, por REST.</b> Tocan una sola cuenta y el
 *       llamante espera la respuesta; la llamada va protegida con Retry + Circuit Breaker
 *       (Resilience4j) y con una clave de idempotencia que hace seguro reintentarla.</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(CuentasServiceProperties.class)
public class PagosServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PagosServiceApplication.class, args);
    }

    /** RestTemplate balanceado: resuelve "http://cuentas-service" contra las instancias registradas en Eureka. */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
