package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.AiJobPort;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.EmbeddingPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Finishes every job at once with deterministic results shaped like contract 16 §4, so grading and RAG code can be
 * built and tested without the AI node: grading scores grow with answer length (80 words = full marks).
 */
public class MockAiJobAdapter implements AiJobPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final double FULL_MARK_WORDS = 80.0;

    private final EmbeddingPort embeddings;

    public MockAiJobAdapter(EmbeddingPort embeddings) {
        this.embeddings = embeddings;
    }

    @Override
    public Optional<AiJobOutcome> submit(UUID jobId, AiJobType type, JsonNode input, String callbackUrl) {
        Object result = switch (type) {
            case GRADE_THREAD -> grade(input);
            case SUMMARIZE_EVALUATION -> Map.of("summary", "Mock summary: overall level is adequate; review the missing key points.",
                    "model", MockInterviewAi.MODEL);
            case GENERATE_QUESTIONS -> generate(input);
            case INDEX_MATERIAL -> index(input);
        };
        return Optional.of(new AiJobOutcome("SUCCEEDED", JSON.valueToTree(result), null, null));
    }

    @Override
    public Optional<AiJobOutcome> poll(UUID jobId) {
        return Optional.empty();
    }

    private static Map<String, Object> grade(JsonNode input) {
        int words = 0;
        for (JsonNode turn : input.path("turns")) {
            words += MockInterviewAi.wordCount(turn.path("transcript").asString(""));
        }
        double ratio = Math.min(1.0, words / FULL_MARK_WORDS);
        List<Map<String, Object>> criteria = new ArrayList<>();
        for (JsonNode c : input.path("rubric").path("criteria")) {
            double max = c.path("max_score").asDouble(10);
            criteria.add(Map.of("criterion_id", c.path("criterion_id").asString(""),
                    "score", Math.round(max * ratio * 4) / 4.0,
                    "justification", "Mock: score proportional to answer length (" + words + " words)."));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("criteria", criteria);
        out.put("strengths", words > 0 ? List.of("Mock: the student answered the question.") : List.of());
        out.put("weaknesses", ratio < 1 ? List.of("Mock: the answer could be more detailed.") : List.of());
        out.put("missing_points", List.of());
        out.put("feedback", "Mock feedback based on answer length.");
        out.put("flags", Map.of("keyword_stuffing_score", 0.0, "self_corrections", List.of()));
        out.put("model", MockInterviewAi.MODEL);
        return out;
    }

    private static Map<String, Object> generate(JsonNode input) {
        int count = input.path("count").asInt(1);
        List<String> blooms = new ArrayList<>();
        input.path("bloom_levels").forEach(b -> blooms.add(b.asString()));
        if (blooms.isEmpty()) {
            blooms.add("UNDERSTAND");
        }
        List<JsonNode> chunks = new ArrayList<>();
        input.path("chunks").forEach(chunks::add);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < count && !chunks.isEmpty(); i++) {
            JsonNode chunk = chunks.get(i % chunks.size());
            String sentence = firstSentence(chunk.path("content").asString(""));
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("content", "Hãy trình bày và giải thích: " + sentence);
            q.put("reference_answer", "- " + sentence);
            q.put("bloom_level", blooms.get(i % blooms.size()));
            q.put("source_chunk_ids", List.of(chunk.path("id").asString("")));
            q.put("rubric", Map.of("name", "Rubric gợi ý " + (i + 1), "criteria", List.of(
                    Map.of("name", "Độ chính xác", "description", "Nêu đúng các ý chính", "max_score", 10, "weight_percent", 60),
                    Map.of("name", "Diễn đạt", "description", "Trình bày rõ ràng, có ví dụ", "max_score", 10, "weight_percent", 40))));
            questions.add(q);
        }
        return Map.of("questions", questions, "model", MockInterviewAi.MODEL);
    }

    private Map<String, Object> index(JsonNode input) {
        String text = "Mock content of " + input.path("file_name").asString("material") + ".";
        return Map.of("page_count", 1, "embedding_model", "mock-embedding", "dimension", embeddings.dimension(),
                "chunks", List.of(Map.of("chunk_index", 0, "content", text, "location_label", "Page 1", "page_from", 1,
                        "page_to", 1, "token_count", MockInterviewAi.wordCount(text),
                        "embedding", embeddings.embed(List.of(text)).getFirst())));
    }

    private static String firstSentence(String text) {
        String sentence = text.strip().split("(?<=[.!?])\\s+", 2)[0];
        return sentence.length() > 200 ? sentence.substring(0, 200) : sentence;
    }
}
