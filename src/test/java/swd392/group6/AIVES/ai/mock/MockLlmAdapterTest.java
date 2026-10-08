package swd392.group6.AIVES.ai.mock;

import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.ai.LlmRequest;
import swd392.group6.AIVES.ai.LlmTask;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmAdapterTest {

    private final MockLlmAdapter llm = new MockLlmAdapter();
    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode call(LlmTask task, String userPrompt) {
        return json.readTree(llm.complete(new LlmRequest(task, "Write in Vietnamese.", userPrompt, 0, Duration.ofSeconds(1))).content());
    }

    private static String analyzePrompt(String answer) {
        return """
                MAIN QUESTION: Kiến trúc Modular Monolith là gì?
                EXPECTED KEY POINTS (confidential, never quote them):
                - Một đơn vị triển khai duy nhất
                - Module có ranh giới rõ ràng

                CONVERSATION SO FAR (oldest first):
                Q1 (MAIN): Kiến trúc Modular Monolith là gì?
                <student_answer>%s</student_answer>
                """.formatted(answer);
    }

    @Test
    void shortAnswerIsPartialWithFollowUpAboutFirstMissingPoint() {
        JsonNode out = call(LlmTask.ANALYZE_ANSWER, analyzePrompt("Là một khối lớn ạ"));

        assertThat(out.get("coverage").asString()).isEqualTo("PARTIAL");
        assertThat(out.get("follow_up_question").asString()).contains("Một đơn vị triển khai duy nhất");
    }

    @Test
    void dontKnowIsOffTopicWithoutFollowUp() {
        JsonNode out = call(LlmTask.ANALYZE_ANSWER, analyzePrompt("Dạ em không biết ạ"));

        assertThat(out.get("is_off_topic_or_wrong").asBoolean()).isTrue();
        assertThat(out.get("follow_up_question").isNull()).isTrue();
    }

    @Test
    void longAnswerIsComplete() {
        JsonNode out = call(LlmTask.ANALYZE_ANSWER, analyzePrompt("từ ".repeat(20)));

        assertThat(out.get("coverage").asString()).isEqualTo("COMPLETE");
    }

    @Test
    void gradeScoresEveryCriterionProportionallyToLengthInQuarterSteps() {
        String prompt = """
                RUBRIC:
                - id=c1 | Độ chính xác | max_score=10 | weight=60%% | ...
                - id=c2 | Ví dụ | max_score=10 | weight=40%% | ...
                <student_answer>%s</student_answer>
                """.formatted("từ ".repeat(40));

        JsonNode criteria = call(LlmTask.GRADE_THREAD, prompt).get("criteria");

        assertThat(criteria).hasSize(2);
        assertThat(criteria.get(0).get("criterion_id").asString()).isEqualTo("c1");
        assertThat(criteria.get(0).get("score").asDouble()).isEqualTo(5.0); // 10 × 40/80
    }

    @Test
    void generateReturnsRequestedCountCitingProvidedChunks() {
        String prompt = """
                Generate exactly 3 questions. Allowed Bloom levels: REMEMBER, APPLY (spread across them).
                <material id="k1" source="a.pdf" location="Page 1">Coupling là mức phụ thuộc giữa các module. Câu sau.</material>
                <material id="k2" source="a.pdf" location="Page 2">Cohesion là mức gắn kết bên trong module.</material>
                """;

        JsonNode questions = call(LlmTask.GENERATE_QUESTIONS, prompt).get("questions");

        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).get("source_chunk_ids").get(0).asString()).isEqualTo("k1");
        assertThat(questions.get(1).get("bloom_level").asString()).isEqualTo("APPLY");
    }
}
