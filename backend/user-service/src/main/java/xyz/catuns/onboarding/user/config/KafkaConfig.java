package xyz.catuns.onboarding.user.config;

import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
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

@Configuration
class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

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

    @Value("${app.kafka.topics.id-correlation}")
    private String idCorrelationTopicName;

    @Bean
    NewTopic idCorrelationTopic(AppProperties appProperties) {
        KafkaTopicProperties topicProperties = appProperties.getKafka();
        return TopicBuilder.name(idCorrelationTopicName)
                .replicas(topicProperties.getReplicas())
                .partitions(topicProperties.getPartitions())
                .build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties();
        // additional ProducerFactory properties;
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

}
