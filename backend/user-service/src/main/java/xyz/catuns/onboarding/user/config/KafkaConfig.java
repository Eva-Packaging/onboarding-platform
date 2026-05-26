package xyz.catuns.onboarding.user.config;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import xyz.catuns.onboarding.user.config.properties.AppProperties;
import xyz.catuns.spring.base.properties.KafkaTopicProperties;

import java.util.Map;

@Configuration
class KafkaConfig {

    @Value("${app.kafka.topics.user-registered}")
    private String userRegisteredTopicName;

    @Bean
    NewTopic userRegisteredTopic(AppProperties appProperties) {
        KafkaTopicProperties topicProperties = appProperties.getKafka();
        return TopicBuilder.name(userRegisteredTopicName)
                .replicas(topicProperties.getReplicas())
                .partitions(topicProperties.getPartitions())
                .build();
    }

    @Bean
    public ProducerFactory<String, SpecificRecord> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, SpecificRecord> kafkaTemplate(ProducerFactory<String, SpecificRecord> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}