package swd392.group6.AIVES.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "application.storage", name = "provider", havingValue = "local", matchIfMissing = true)
    StoragePort localFileStorage(@Value("${application.storage.local-dir:./data/storage}") String dir) {
        return new LocalFileStorageAdapter(Path.of(dir));
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.storage", name = "provider", havingValue = "memory")
    StoragePort inMemoryStorage() {
        return new InMemoryStorageAdapter();
    }
}
