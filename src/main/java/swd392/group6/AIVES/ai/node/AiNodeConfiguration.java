package swd392.group6.AIVES.ai.node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import swd392.group6.AIVES.ai.AiNodeProperties;

/** {@code application.ai.mode=node}: every AI port is served by the AI node (D41, contract 16). */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "application.ai", name = "mode", havingValue = "node")
class AiNodeConfiguration {

    @Bean
    AiNodeClient aiNodeClient(AiNodeProperties properties,
                              @Value("${application.ai.embedding.dimension:1536}") int dimension) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.token() == null || properties.token().isBlank()
                || properties.callbackSecret() == null || properties.callbackSecret().isBlank()) {
            throw new IllegalStateException("AI mode = node needs AI_NODE_URL, AI_NODE_TOKEN and AI_NODE_CALLBACK_SECRET");
        }
        log.info("AI mode = NODE: {}", properties.baseUrl());
        return new AiNodeClient(properties, dimension);
    }
}
