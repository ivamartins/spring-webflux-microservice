package com.codesolutions.kafka;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal @SpringBootApplication for the embedded-kafka contract test.
 *
 * Scans only the kafka package so we don't pull in the full app
 * (which would try to connect to Postgres / Mongo / Redis).
 */
@SpringBootApplication
@ComponentScan(basePackageClasses = OrderEventPublisher.class)
public class KafkaContractTestConfig {
}
