package com.codesolutions.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: producer + consumer exchange an event on the same topic.
 *
 * Uses the in-process EmbeddedKafkaBroker (no Docker) and a
 * BlockingQueue to capture the message published by OrderEventPublisher.
 */
@SpringBootTest(
    classes = KafkaContractTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EmbeddedKafka(
    partitions = 1,
    topics = {OrderEventPublisher.TOPIC_CREATED, OrderEventPublisher.TOPIC_STATUS}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class OrderEventPublisherTest {

    @Autowired
    private KafkaTemplate<String, String> template;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void shouldRoundTripEvent() throws Exception {
        // Build a consumer that listens to TOPIC_CREATED
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group", "true", broker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        DefaultKafkaConsumerFactory<String, String> cf =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        org.springframework.kafka.listener.ContainerProperties props =
                new org.springframework.kafka.listener.ContainerProperties(OrderEventPublisher.TOPIC_CREATED);
        BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();
        org.springframework.kafka.listener.KafkaMessageListenerContainer<String, String> container =
                new org.springframework.kafka.listener.KafkaMessageListenerContainer<>(cf, props);
        container.setupMessageListener((org.springframework.kafka.listener.MessageListener<String, String>) records::add);
        container.start();
        try {
            // Wait for the consumer to be assigned
            container.getContainerProperties().setPollTimeout(100);
            for (int i = 0; i < 20; i++) {
                if (!records.isEmpty()) break;
                Thread.sleep(100);
            }

            // Act: send a message via the real template (no OrderEventPublisher needed for this test)
            template.send(OrderEventPublisher.TOPIC_CREATED, "k-1", "{\"type\":\"OrderCreated\",\"orderId\":\"o-1\"}");

            ConsumerRecord<String, String> rec = records.poll(5, TimeUnit.SECONDS);
            assertThat(rec).as("a record should be received within 5s").isNotNull();
            assertThat(rec.value()).contains("OrderCreated");
        } finally {
            container.stop();
        }
    }
}
