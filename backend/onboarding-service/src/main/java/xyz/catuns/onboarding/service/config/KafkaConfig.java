package xyz.catuns.onboarding.service.config;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import xyz.catuns.onboarding.service.config.properties.AppProperties;
import xyz.catuns.spring.base.properties.KafkaTopicProperties;

import java.util.Map;

@Configuration
class KafkaConfig {

    @Value("${app.kafka.topics.id-correlation}")
    private String idCorrelationTopicName;

    @Value("${app.kafka.topics.github-provisioning}")
    private String githubProvisioningTopicName;

    @Value("${app.kafka.topics.atlassian-provisioning}")
    private String atlassianProvisioningTopicName;

    @Value("${app.kafka.topics.onboarding-lifecycle}")
    private String onboardingLifecycleTopicName;

    @Value("${spring.kafka.properties.schema-registry-url:http://localhost:9090}")
    private String schemaRegistryUrl;

    @Bean
    NewTopic idCorrelationTopic(AppProperties appProperties) {
        KafkaTopicProperties tp = appProperties.getKafka();
        return TopicBuilder.name(idCorrelationTopicName)
                .replicas(tp.getReplicas())
                .partitions(tp.getPartitions())
                .build();
    }

    @Bean
    NewTopic githubProvisioningTopic(AppProperties appProperties) {
        KafkaTopicProperties tp = appProperties.getKafka();
        return TopicBuilder.name(githubProvisioningTopicName)
                .replicas(tp.getReplicas())
                .partitions(tp.getPartitions())
                .build();
    }

    @Bean
    NewTopic atlassianProvisioningTopic(AppProperties appProperties) {
        KafkaTopicProperties tp = appProperties.getKafka();
        return TopicBuilder.name(atlassianProvisioningTopicName)
                .replicas(tp.getReplicas())
                .partitions(tp.getPartitions())
                .build();
    }

    @Bean
    NewTopic onboardingLifecycleTopic(AppProperties appProperties) {
        KafkaTopicProperties tp = appProperties.getKafka();
        return TopicBuilder.name(onboardingLifecycleTopicName)
                .replicas(tp.getReplicas())
                .partitions(tp.getPartitions())
                .config(TopicConfig.RETENTION_MS_CONFIG, "1209600000") // 14 days
                .build();
    }

    @Bean
    public ProducerFactory<String, SpecificRecord> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        config.put("schema.registry.url", schemaRegistryUrl);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, SpecificRecord> kafkaTemplate(ProducerFactory<String, SpecificRecord> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, SpecificRecord> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
//        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        config.put("schema.registry.url", schemaRegistryUrl);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> kafkaListenerContainerFactory(
            ConsumerFactory<String, SpecificRecord> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, SpecificRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}