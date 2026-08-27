package co.riwi.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Flyway auto-configuration is excluded on purpose: migrations run through
 * the dedicated {@code migrate} Compose service as rw_migrator (D-07), never
 * through this application's own rw_app datasource, which lacks the DDL
 * privilege to run them anyway.
 */
@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
@ConfigurationPropertiesScan
public class MessagingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
