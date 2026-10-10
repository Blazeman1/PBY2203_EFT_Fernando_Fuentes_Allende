package com.bancoxyz.cuentas.config;

import com.bancoxyz.cuentas.saga.dto.DebitoAplicadoEvent;
import com.bancoxyz.cuentas.saga.dto.TransferenciaSolicitadaEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * cuentas-service es PRODUCTOR (DebitoAplicadoEvent, TransferenciaResultadoEvent) y CONSUMIDOR
 * (TransferenciaSolicitadaEvent, DebitoAplicadoEvent) de los eventos de la saga.
 *
 * <p>Una fabrica de listener por tipo de evento consumido, y sin header {@code __TypeId__} en el
 * productor: cada consumidor sabe, por su propio topico, que tipo le corresponde deserializar
 * contra SU copia local del contrato, sin depender del nombre de clase de otro microservicio.</p>
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Espera la confirmacion de todas las replicas en sincronia antes de dar el envio por exitoso.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // Por defecto send() puede bloquear hasta 60 s esperando metadatos si el broker esta caido.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, TransferenciaSolicitadaEvent> solicitudConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(propsConsumidor(TransferenciaSolicitadaEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferenciaSolicitadaEvent> solicitudListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransferenciaSolicitadaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(solicitudConsumerFactory());
        factory.setCommonErrorHandler(manejadorDeErrores());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, DebitoAplicadoEvent> debitoAplicadoConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(propsConsumidor(DebitoAplicadoEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DebitoAplicadoEvent> debitoAplicadoListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DebitoAplicadoEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(debitoAplicadoConsumerFactory());
        factory.setCommonErrorHandler(manejadorDeErrores());
        return factory;
    }

    /**
     * Politica de reintento de los listeners de la saga. Por defecto Spring Kafka reintenta 9
     * veces SIN espera y luego descarta el mensaje: una caida del broker de menos de un minuto,
     * justo despues de confirmar un debito, bastaria para perder el evento siguiente y dejar una
     * transferencia a medio camino. Aqui se reintenta cada 2 segundos durante 10 minutos (300
     * intentos); como cada paso es idempotente, reintentar nunca mueve dinero dos veces.
     */
    private DefaultErrorHandler manejadorDeErrores() {
        return new DefaultErrorHandler(new FixedBackOff(2000L, 300L));
    }

    private Map<String, Object> propsConsumidor(Class<?> tipoEsperado) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bancoxyz.cuentas.saga.dto");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, tipoEsperado.getName());
        return props;
    }
}
