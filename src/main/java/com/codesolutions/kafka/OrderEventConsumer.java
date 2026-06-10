package com.codesolutions.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo Kafka consumer that ingests OrderEvent messages and keeps a
 * in-memory snapshot of the most-recent order per customer.
 *
 * In production: would forward to Mongo, ES, or another downstream
 * system. Here it's used to demonstrate a working consumer loop and
 * verify the producer/consumer contract in tests.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final Map<String, String> latestEventByCustomer = new ConcurrentHashMap<>();

    @KafkaListener(topics = OrderEventPublisher.TOPIC_CREATED, groupId = "webflux-ms")
    public void onCreated(Map<String, Object> event) {
        if (event == null) return;
        String customer = String.valueOf(event.get("customerId"));
        latestEventByCustomer.put(customer, String.valueOf(event.get("type")));
        log.info("Consumed OrderCreated: customer={} type={}", customer, event.get("type"));
    }

    @KafkaListener(topics = OrderEventPublisher.TOPIC_STATUS, groupId = "webflux-ms")
    public void onStatusChanged(Map<String, Object> event) {
        if (event == null) return;
        log.info("Consumed OrderStatusChanged: orderId={} status={}", event.get("orderId"), event.get("status"));
    }

    public Map<String, String> getLatestEventByCustomer() {
        return Map.copyOf(latestEventByCustomer);
    }
}
