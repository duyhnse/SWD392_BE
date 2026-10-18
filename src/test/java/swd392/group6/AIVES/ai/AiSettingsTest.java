package swd392.group6.AIVES.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** D70: the admin's AI settings become the {@code options} object of every AI node call. */
class AiSettingsTest {

    @Test
    @SuppressWarnings("unchecked")
    void storedValuesBecomeNestedOptionsAndBadOnesFallBack() {
        Map<String, Optional<String>> stored = Map.of("ai.temperature.analysis", Optional.of("0.4"),
                "ai.timeout.turn_budget_sec", Optional.of("99"), "ai.voice.VI", Optional.of("Puck"));
        Map<String, Object> o = AiSettings.options(k -> stored.getOrDefault(k, Optional.empty()));
        assertThat((Map<String, Object>) o.get("temperature")).containsEntry("analysis", 0.4);
        assertThat((Map<String, Object>) o.get("timeouts")).containsEntry("turn_budget_sec", 11.0); // out of range → default
        assertThat((Map<String, Object>) o.get("voices")).containsEntry("VI", "Puck");
        assertThat((Map<String, Object>) o.get("models")).containsEntry("stt", "gemini-3.5-transcribe");
        assertThat(o).containsEntry("interview_reasoning", "minimal");
    }

    @Test
    void problemsNameTheRule() {
        assertThat(AiSettings.problem("ai.rag.top_k", "20")).contains("between 4 and 12");
        assertThat(AiSettings.problem("ai.model.interview_fallback", "")).isNull();
        assertThat(AiSettings.problem("ai.model.interview", "")).isNotNull();
        assertThat(AiSettings.problem("ai.interview_reasoning", "low")).isNull();
    }
}
