package com.bancoxyz.clientes.evento;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publica alertas de seguridad en Kafka cuando cambia informacion sensible de un cliente.
 *
 * <p>Protegido con circuit breaker (Resilience4j). <b>Comportamiento alternativo ante falla:</b>
 * la alerta es informativa y no forma parte del camino critico, asi que si el broker no esta
 * disponible la operacion del cliente (ya confirmada en la base de datos) NO se deshace: la
 * alerta se registra en el log como no enviada y la respuesta al llamante sigue siendo exitosa.
 * Con el circuito abierto ni siquiera se intenta publicar, evitando sumar varios segundos de
 * espera a cada actualizacion de perfil mientras Kafka este caido.</p>
 */
@Component
public class AlertaSeguridadPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertaSeguridadPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicoAlertas;

    public AlertaSeguridadPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                    @Value("${kafka.topics.alertas-seguridad}") String topicoAlertas) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicoAlertas = topicoAlertas;
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publicarFallback")
    public void publicar(String tipo, String severidad, long clienteId, String detalle) {
        AlertaSeguridadEvent evento = new AlertaSeguridadEvent(UUID.randomUUID().toString(), tipo, severidad,
                clienteId, null, detalle, "clientes-service", LocalDateTime.now().toString());
        try {
            kafkaTemplate.send(topicoAlertas, String.valueOf(clienteId), evento).get(3, TimeUnit.SECONDS);
            log.info("Alerta de seguridad publicada: tipo={} clienteId={}", tipo, clienteId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido mientras se publicaba la alerta.", e);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar la alerta de seguridad.", e);
        }
    }

    private void publicarFallback(String tipo, String severidad, long clienteId, String detalle, Throwable t) {
        log.warn("Alerta de seguridad NO enviada (mensajeria no disponible): tipo={} clienteId={} detalle=\"{}\" causa={}",
                tipo, clienteId, detalle, t.toString());
    }
}
