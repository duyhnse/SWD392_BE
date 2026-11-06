package swd392.group6.AIVES.ai.node;

import org.springframework.beans.factory.ObjectProvider;
import swd392.group6.AIVES.ai.AiSettingsSource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import swd392.group6.AIVES.ai.AiNodeProperties;

/**
 * Node 1 has no AI of its own: every AI port is the HTTP client of the AI node (D41, contract 16). Without
 * AI_NODE_URL the app still starts and AI features wait (jobs stay queued, then fail as AI_NODE_UNAVAILABLE).
 * Tests replace the client ({@code application.ai-node.enabled=false}).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "application.ai-node", name = "enabled", havingValue = "true", matchIfMissing = true)
class AiNodeConfiguration {

    @Bean
    AiNodeClient aiNodeClient(AiNodeProperties properties, ObjectProvider<AiSettingsSource> settings) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            log.warn("AI_NODE_URL is not set: AI features (material indexing, question generation, AI grading, "
                    + "the interview) are unavailable until the AI node is configured");
        } else {
            log.info("AI node: {}", properties.baseUrl());
        }
        return new AiNodeClient(properties, settings.getIfAvailable());
    }
}
