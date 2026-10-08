package swd392.group6.AIVES.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Throw-away PostgreSQL seeded with the same schema script the real database uses. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("init-scripts/init_schema.sql"),
                        "/docker-entrypoint-initdb.d/init_schema.sql");
    }
}
