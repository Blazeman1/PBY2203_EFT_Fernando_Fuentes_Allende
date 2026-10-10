package com.bancoxyz.bff.notificaciones.config;

import com.bancoxyz.bff.notificaciones.dto.AlertaSeguridadEvent;
import com.bancoxyz.bff.notificaciones.dto.PagoCompletadoEvent;
import com.bancoxyz.bff.notificaciones.dto.TransferenciaResultadoEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Una fabrica de listener por tipo de evento consumido (resultado de transferencias, pagos
 * completados y alertas de seguridad). Configuracion explicita para usar un deserializador JSON
 * con manejo de errores: si un mensaje llega corrupto o con otro esquema,
 * {@link ErrorHandlingDeserializer} evita que ese unico mensaje detenga al listener completo.
 *
 * <p>No se confia en el header {@code __TypeId__} del productor (llevaria el nombre de una clase
 * de OTRO microservicio, inexistente aqui): cada fabrica deserializa siempre contra la copia
 * local del contrato que corresponde a su topico.</p>
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferenciaResultadoEvent> kafkaListenerContainerFactory() {
        return fabricaPara(TransferenciaResultadoEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PagoCompletadoEvent> pagoCompletadoListenerContainerFactory() {
        return fabricaPara(PagoCompletadoEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AlertaSeguridadEvent> alertaSeguridadListenerContainerFactory() {
        return fabricaPara(AlertaSeguridadEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> fabricaPara(Class<T> tipoEvento) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.bancoxyz.bff.notificaciones.dto");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, tipoEvento.getName());

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        return factory;
    }
}
