package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.LlmPort;
import swd392.group6.AIVES.ai.LlmRequest;
import swd392.group6.AIVES.ai.LlmResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic stand-in for the LLM (02_ARCHITECTURE.md §5). It reads the rendered prompts of 10_AI_PROMPTS.md,
 * so callers exercise their real prompt-building and JSON-validation code in tests and offline demos.
 */
public class MockLlmAdapter implements LlmPort {

    static final String MODEL = "mock-llm";

    private static final Pattern STUDENT_ANSWER = Pattern.compile("<student_answer>(.*?)</student_answer>", Pattern.DOTALL);
    private static final Pattern KEY_POINTS = Pattern.compile("EXPECTED KEY POINTS[^\\n]*\\n(.*?)\\n\\s*\\n", Pattern.DOTALL);
    private static final Pattern CRITERION = Pattern.compile("-\\s*id=(\\S+)\\s*\\|[^|]*\\|\\s*max_score=([\\d.]+)");
    private static final Pattern COUNT = Pattern.compile("Generate exactly (\\d+) questions");
    private static final Pattern BLOOMS = Pattern.compile("Allowed Bloom levels:\\s*([A-Z_,\\s]+?)\\s*\\(");
    private static final Pattern MATERIAL = Pattern.compile("<material id=\"([^\"]+)\"[^>]*>\\s*(.*?)\\s*</material>", Pattern.DOTALL);
    private static final int SHORT_ANSWER_WORDS = 15;
    private static final double FULL_MARK_WORDS = 80.0;

    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public LlmResponse complete(LlmRequest request) {
        Map<String, Object> result = switch (request.task()) {
            case ANALYZE_ANSWER -> analyze(request);
            case GRADE_THREAD -> grade(request.userPrompt());
            case GENERATE_QUESTIONS -> generate(request.userPrompt());
            case SUMMARIZE_EVALUATION -> Map.of("summary", "Mock summary: overall level is adequate; review the missing key points.");
        };
        return new LlmResponse(json.writeValueAsString(result), MODEL, 0);
    }

    private Map<String, Object> analyze(LlmRequest request) {
        List<String> answers = studentAnswers(request.userPrompt());
        String latest = answers.isEmpty() ? "" : answers.getLast();
        String lower = latest.toLowerCase(Locale.ROOT);
        List<String> keyPoints = keyPoints(request.userPrompt());
        boolean english = request.systemPrompt() != null && request.systemPrompt().contains("English");

        Map<String, Object> out = new LinkedHashMap<>();
        boolean dontKnow = lower.contains("không biết") || lower.contains("i don't know") || lower.contains("i do not know");
        if (dontKnow) {
            out.put("coverage", "INSUFFICIENT");
            out.put("covered_points", List.of());
            out.put("missing_points", keyPoints);
            putFlags(out, true);
            out.put("follow_up_question", null);
            out.put("rationale", "Mock: student said they do not know.");
        } else if (wordCount(latest) < SHORT_ANSWER_WORDS) {
            String point = keyPoints.isEmpty() ? "ý chính của câu hỏi" : keyPoints.getFirst();
            out.put("coverage", "PARTIAL");
            out.put("covered_points", List.of());
            out.put("missing_points", keyPoints);
            putFlags(out, false);
            out.put("follow_up_question", english
                    ? "Could you explain more about: " + point + "?"
                    : "Em có thể giải thích rõ hơn về ý: " + point + "?");
            out.put("rationale", "Mock: answer shorter than " + SHORT_ANSWER_WORDS + " words.");
        } else {
            out.put("coverage", "COMPLETE");
            out.put("covered_points", keyPoints);
            out.put("missing_points", List.of());
            putFlags(out, false);
            out.put("follow_up_question", null);
            out.put("rationale", "Mock: answer long enough.");
        }
        return out;
    }

    private static void putFlags(Map<String, Object> out, boolean offTopicOrWrong) {
        out.put("is_vague", false);
        out.put("is_contradictory", false);
        out.put("is_bluffing", false);
        out.put("is_off_topic_or_wrong", offTopicOrWrong);
    }

    private Map<String, Object> grade(String prompt) {
        int words = studentAnswers(prompt).stream().mapToInt(MockLlmAdapter::wordCount).sum();
        double ratio = Math.min(1.0, words / FULL_MARK_WORDS);
        List<Map<String, Object>> criteria = new ArrayList<>();
        Matcher m = CRITERION.matcher(prompt);
        while (m.find()) {
            double max = Double.parseDouble(m.group(2));
            criteria.add(Map.of(
                    "criterion_id", m.group(1),
                    "score", Math.round(max * ratio * 4) / 4.0,
                    "justification", "Mock: score proportional to answer length (" + words + " words)."));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("criteria", criteria);
        out.put("strengths", words > 0 ? List.of("Mock: the student answered the question.") : List.of());
        out.put("weaknesses", ratio < 1 ? List.of("Mock: the answer could be more detailed.") : List.of());
        out.put("missing_points", List.of());
        out.put("feedback", "Mock feedback based on answer length.");
        return out;
    }

    private Map<String, Object> generate(String prompt) {
        Matcher countMatcher = COUNT.matcher(prompt);
        int count = countMatcher.find() ? Integer.parseInt(countMatcher.group(1)) : 1;
        Matcher bloomMatcher = BLOOMS.matcher(prompt);
        List<String> blooms = bloomMatcher.find()
                ? List.of(bloomMatcher.group(1).trim().split("\\s*,\\s*"))
                : List.of("UNDERSTAND");
        List<String[]> chunks = new ArrayList<>();
        Matcher material = MATERIAL.matcher(prompt);
        while (material.find()) {
            chunks.add(new String[]{material.group(1), firstSentence(material.group(2))});
        }

        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < count && !chunks.isEmpty(); i++) {
            String[] chunk = chunks.get(i % chunks.size());
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("content", "Hãy trình bày và giải thích: " + chunk[1]);
            q.put("reference_answer", "- " + chunk[1]);
            q.put("bloom_level", blooms.get(i % blooms.size()));
            q.put("source_chunk_ids", List.of(chunk[0]));
            q.put("rubric", Map.of(
                    "name", "Rubric gợi ý " + (i + 1),
                    "criteria", List.of(
                            Map.of("name", "Độ chính xác", "description", "Nêu đúng các ý chính", "max_score", 10, "weight_percent", 60),
                            Map.of("name", "Diễn đạt", "description", "Trình bày rõ ràng, có ví dụ", "max_score", 10, "weight_percent", 40))));
            questions.add(q);
        }
        return Map.of("questions", questions);
    }

    private static List<String> studentAnswers(String prompt) {
        List<String> answers = new ArrayList<>();
        Matcher m = STUDENT_ANSWER.matcher(prompt == null ? "" : prompt);
        while (m.find()) {
            answers.add(m.group(1).trim());
        }
        return answers;
    }

    private static List<String> keyPoints(String prompt) {
        Matcher m = KEY_POINTS.matcher(prompt == null ? "" : prompt);
        if (!m.find()) {
            return List.of();
        }
        return m.group(1).lines()
                .map(String::trim)
                .filter(line -> line.startsWith("-"))
                .map(line -> line.substring(1).trim())
                .toList();
    }

    private static String firstSentence(String text) {
        String sentence = text.strip().split("(?<=[.!?])\\s+", 2)[0];
        return sentence.length() > 200 ? sentence.substring(0, 200) : sentence;
    }

    static int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
