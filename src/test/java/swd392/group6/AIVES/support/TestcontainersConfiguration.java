package swd392.group6.AIVES.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Throw-away PostgreSQL (+pgvector) migrated by Flyway, plus test doubles for outgoing mail. */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestUsers.class)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @Primary
    RecordingMailPort recordingMailPort() {
        return new RecordingMailPort();
    }
}
