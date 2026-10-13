package swd392.group6.AIVES.support;

import org.springframework.boot.test.context.TestConfiguration;
import swd392.group6.AIVES.ai.AiJobPort;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.AiMaterialIndexPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double of the AI node (contract 16): finishes every job at once with a contract-shaped answer, so node 1's
 * own behaviour is tested without node 2. Node 2's real logic (parsing, retrieval, retries) is tested in its repo.
 * Switches: a file name with "broken" / "scan" fails indexing (UNSUPPORTED_FILE / NO_TEXT_LAYER); a focus text with
 * "#no-context", "#fail" or "#bad-citation" answers NO_CONTEXT, a failed LLM, or a draft citing another material.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAiNode implements AiJobPort, AiMaterialIndexPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    public final List<UUID> deletedMaterials = new CopyOnWriteArrayList<>();

    @Override
    public Optional<AiJobOutcome> submit(UUID jobId, AiJobType type, JsonNode input, String callbackUrl) {
        return Optional.of(switch (type) {
            case INDEX_MATERIAL -> index(input);
            case GENERATE_QUESTIONS -> generate(input);
            case GRADE_THREAD -> ok(Map.of("criteria", List.of(), "strengths", List.of(), "weaknesses", List.of(),
                    "missing_points", List.of(), "feedback", "fake", "model", "fake"));
            case SUMMARIZE_EVALUATION -> ok(Map.of("summary", "fake summary", "model", "fake"));
        });
    }

    @Override
    public Optional<AiJobOutcome> poll(UUID jobId) {
        return Optional.empty();
    }

    @Override
    public void deleteMaterial(UUID materialId) {
        deletedMaterials.add(materialId);
    }

    private static AiJobOutcome index(JsonNode input) {
        String name = input.path("file_name").asString("");
        if (name.contains("broken")) {
            return new AiJobOutcome("FAILED", null, "UNSUPPORTED_FILE", "The file could not be read");
        }
        if (name.contains("scan")) {
            return new AiJobOutcome("FAILED", null, "NO_TEXT_LAYER", "The file has no text layer");
        }
        return ok(Map.of("page_count", 2, "chunk_count", 3, "embedding_model", "fake"));
    }

    private static AiJobOutcome generate(JsonNode input) {
        String focus = input.path("focus_text").asString("");
        if (focus.contains("#no-context")) {
            return new AiJobOutcome("FAILED", null, "NO_CONTEXT", "Selected materials do not cover this topic");
        }
        if (focus.contains("#fail")) {
            return new AiJobOutcome("FAILED", null, "INVALID_OUTPUT", "The LLM answer was invalid twice");
        }
        String material = focus.contains("#bad-citation") ? UUID.randomUUID().toString()
                : input.path("material_ids").path(0).asString();
        List<String> blooms = new ArrayList<>();
        input.path("bloom_levels").forEach(b -> blooms.add(b.asString()));
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < input.path("count").asInt(1); i++) {
            questions.add(Map.of(
                    "content", "Câu hỏi " + (i + 1) + " về " + input.path("topic").path("name").asString(),
                    "reference_answer", "- Ý chính " + (i + 1),
                    "bloom_level", blooms.isEmpty() ? "UNDERSTAND" : blooms.get(i % blooms.size()),
                    "sources", List.of(Map.of("material_id", material, "location_label", "Page " + (i + 1),
                            "excerpt", "Đoạn trích " + (i + 1))),
                    "rubric", Map.of("name", "Rubric gợi ý", "criteria", List.of(
                            Map.of("name", "Độ chính xác", "description", "Đúng ý", "max_score", 10, "weight_percent", 60),
                            Map.of("name", "Diễn đạt", "description", "Rõ ràng", "max_score", 10, "weight_percent", 40)))));
        }
        return ok(Map.of("questions", questions, "model", "fake"));
    }

    private static AiJobOutcome ok(Object result) {
        return new AiJobOutcome("SUCCEEDED", JSON.valueToTree(result), null, null);
    }
}
