package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.AiJobPort;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.EmbeddingPort;
import swd392.group6.AIVES.storage.StoragePort;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Finishes every job at once with deterministic results shaped like contract 16 §4, so grading and RAG code can be
 * built and tested without the AI node: grading scores grow with answer length (80 words = full marks); indexing
 * really extracts and chunks the stored PDF / PPTX / DOCX ({@link MockDocumentText}); generation turns retrieved
 * chunks into draft questions. A {@code focus_text} containing {@value #INVALID} returns malformed output and
 * {@value #FAIL} a failed job, so the error paths can be exercised (04 E4, E5).
 */
public class MockAiJobAdapter implements AiJobPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final double FULL_MARK_WORDS = 80.0;

    static final String INVALID = "#mock-invalid";
    static final String FAIL = "#mock-fail";

    private final EmbeddingPort embeddings;
    private final StoragePort storage;

    public MockAiJobAdapter(EmbeddingPort embeddings, StoragePort storage) {
        this.embeddings = embeddings;
        this.storage = storage;
    }

    @Override
    public Optional<AiJobOutcome> submit(UUID jobId, AiJobType type, JsonNode input, String callbackUrl) {
        String focus = input.path("focus_text").asString("");
        if (type == AiJobType.GENERATE_QUESTIONS && focus.contains(FAIL)) {
            return Optional.of(new AiJobOutcome("FAILED", null, "LLM_UNAVAILABLE", "Mock: the LLM is down"));
        }
        if (type == AiJobType.GENERATE_QUESTIONS && focus.contains(INVALID)) {
            return Optional.of(new AiJobOutcome("SUCCEEDED", JSON.valueToTree(Map.of("questions", "not a list")), null, null));
        }
        if (type == AiJobType.INDEX_MATERIAL) {
            return Optional.of(index(input));
        }
        Object result = switch (type) {
            case GRADE_THREAD -> grade(input);
            case SUMMARIZE_EVALUATION -> Map.of("summary", "Mock summary: overall level is adequate; review the missing key points.",
                    "model", MockInterviewAi.MODEL);
            case GENERATE_QUESTIONS -> generate(input);
            case INDEX_MATERIAL -> throw new IllegalStateException();
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
            String bloom = blooms.get(i % blooms.size());
            boolean en = "EN".equals(input.path("language").asString("VI"));
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("content", stem(bloom, en) + " \"" + sentence + "\"");
            q.put("reference_answer", "- " + sentence + "\n- " + (en ? "Explains it in their own words with an example"
                    : "Giải thích bằng lời của mình và có ví dụ"));
            q.put("bloom_level", bloom);
            q.put("source_chunk_ids", List.of(chunk.path("id").asString("")));
            q.put("rubric", Map.of("name", "Rubric gợi ý " + (i + 1), "criteria", List.of(
                    Map.of("name", "Độ chính xác", "description", "Nêu đúng các ý chính", "max_score", 10, "weight_percent", 60),
                    Map.of("name", "Diễn đạt", "description", "Trình bày rõ ràng, có ví dụ", "max_score", 10, "weight_percent", 40))));
            questions.add(q);
        }
        return Map.of("questions", questions, "model", MockInterviewAi.MODEL);
    }

    private AiJobOutcome index(JsonNode input) {
        String url = input.path("file_url").asString("");
        String key = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("key");
        byte[] data = key == null ? null
                : storage.get(URLDecoder.decode(key, StandardCharsets.UTF_8)).map(o -> o.data()).orElse(null);
        if (data == null) {
            return new AiJobOutcome("FAILED", null, "FILE_UNAVAILABLE", "Mock: the material file could not be read");
        }
        List<MockDocumentText.Chunk> chunks;
        int pages;
        try {
            List<MockDocumentText.Part> parts = MockDocumentText.parts(data, input.path("content_type").asString(""),
                    input.path("file_name").asString(""));
            pages = parts.size();
            chunks = MockDocumentText.chunks(parts, input.path("chunking").path("size_tokens").asInt(350),
                    input.path("chunking").path("overlap_tokens").asInt(50));
        } catch (MockDocumentText.NoTextException e) {
            return new AiJobOutcome("FAILED", null, "NO_TEXT_LAYER", "The file has no text layer (OCR is not supported)");
        } catch (IOException | RuntimeException e) {
            return new AiJobOutcome("FAILED", null, "UNSUPPORTED_FILE", "The file could not be read: " + e.getMessage());
        }
        List<float[]> vectors = embeddings.embed(chunks.stream().map(MockDocumentText.Chunk::content).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            MockDocumentText.Chunk c = chunks.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunk_index", i);
            row.put("content", c.content());
            row.put("location_label", c.label());
            row.put("page_from", c.pageFrom() > 0 ? c.pageFrom() : null);
            row.put("page_to", c.pageTo() > 0 ? c.pageTo() : null);
            row.put("token_count", c.words());
            row.put("embedding", vectors.get(i));
            out.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page_count", pages);
        result.put("embedding_model", "mock-embedding");
        result.put("dimension", embeddings.dimension());
        result.put("chunks", out);
        return new AiJobOutcome("SUCCEEDED", JSON.valueToTree(result), null, null);
    }

    private static String stem(String bloom, boolean en) {
        return switch (bloom) {
            case "REMEMBER" -> en ? "State what the material says about:" : "Hãy nêu lại nội dung chính của ý sau:";
            case "APPLY" -> en ? "Apply this idea to a concrete case:" : "Hãy áp dụng ý sau vào một tình huống cụ thể:";
            case "ANALYZE" -> en ? "Analyse the trade-offs behind:" : "Hãy phân tích ưu, nhược điểm và đánh đổi của ý sau:";
            default -> en ? "Explain in your own words:" : "Hãy giải thích bằng lời của mình:";
        };
    }

    private static String firstSentence(String text) {
        String sentence = text.strip().split("(?<=[.!?])\\s+", 2)[0];
        return sentence.length() > 200 ? sentence.substring(0, 200) : sentence;
    }
}
