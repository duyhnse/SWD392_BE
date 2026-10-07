package swd392.group6.AIVES.ai.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import swd392.group6.AIVES.ai.EmbeddingPort;
import swd392.group6.AIVES.ai.LlmPort;
import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.TextToSpeechPort;

/** {@code application.ai.mode=mock} (the default): every AI port uses its deterministic mock. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "application.ai", name = "mode", havingValue = "mock", matchIfMissing = true)
class MockAiConfiguration {

    MockAiConfiguration() {
        log.warn("AI mode = MOCK: LLM, embeddings, STT and TTS return canned results (no provider is called)");
    }

    @Bean
    LlmPort mockLlm() {
        return new MockLlmAdapter();
    }

    @Bean
    EmbeddingPort mockEmbedding(@Value("${application.ai.embedding.dimension:1536}") int dimension) {
        return new MockEmbeddingAdapter(dimension);
    }

    @Bean
    SpeechToTextPort mockSpeechToText() {
        return new MockSpeechToTextAdapter();
    }

    @Bean
    TextToSpeechPort mockTextToSpeech() {
        return new MockTextToSpeechAdapter();
    }
}
