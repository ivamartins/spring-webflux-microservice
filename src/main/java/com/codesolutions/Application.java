package com.codesolutions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring WebFlux microservice.
 *
 * Maps to the Java Sr (Híbrido) JD:
 *  - Java 21 (set in pom.xml)
 *  - Spring Boot 3 + WebFlux
 *  - Microservice + Messaging (Kafka) + legacy integrations
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
