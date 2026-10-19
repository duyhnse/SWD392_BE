package swd392.group6.AIVES.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * AI settings the admin edits on the Settings page (D70), stored as {@code system_settings} rows {@code ai.*} and sent
 * to the AI node as the {@code options} object of every call (contract 1.2.0). Ranges keep a value from breaking the
 * exam flow; the AI node clamps again on its side. Defaults: OpenRouter model ids (D71), timings measured on prod (D70).
 */
public final class AiSettings {

    /**
     * @param kind   MODEL (an id from the AI node's catalogue), VOICE, NUMBER or CHOICE
     * @param group  catalogue list a MODEL is picked from (llm, stt, tts, embedding)
     * @param path   where the value goes in {@code options} ({@code group.key})
     */
    public record Rule(String key, String kind, String group, Double min, Double max, List<String> choices,
                       String defaultValue, String path, String unit) {
    }

    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._\\-/]{1,99}");
    private static final Pattern VOICE = Pattern.compile("[A-Za-z0-9_\\-]{2,60}");

    public static final List<Rule> RULES = List.of(
            model("ai.model.interview", "llm", "google/gemini-3.5-flash-lite", "models.interview"),
            model("ai.model.interview_fallback", "llm", "google/gemini-3.1-flash-lite", "models.interview_fallback"),
            model("ai.model.generation", "llm", "google/gemini-3.8-flash", "models.generation"),
            model("ai.model.grading", "llm", "google/gemini-3.8-flash", "models.grading"),
            model("ai.model.stt", "stt", "google/gemini-3.5-transcribe", "models.stt"),
            model("ai.model.tts", "tts", "google/gemini-3.8-flash-tts", "models.tts"),
            model("ai.model.embedding", "embedding", "google/gemini-embedding-2", "models.embedding"),
            new Rule("ai.voice.VI", "VOICE", "voices", null, null, null, "Kore", "voices.VI", null),
            new Rule("ai.voice.EN", "VOICE", "voices", null, null, null, "Kore", "voices.EN", null),
            number("ai.temperature.analysis", 0, 1, "0.2", "temperature.analysis", null),
            number("ai.temperature.generation", 0, 1.2, "0.7", "temperature.generation", null),
            number("ai.temperature.grading", 0, 1, "0", "temperature.grading", null),
            number("ai.temperature.summary", 0, 1, "0.3", "temperature.summary", null),
            number("ai.timeout.turn_budget_sec", 8, 20, "11", "timeouts.turn_budget_sec", "s"),
            number("ai.timeout.llm_interview_sec", 3, 15, "8", "timeouts.llm_interview_sec", "s"),
            number("ai.timeout.stt_sec", 5, 20, "10", "timeouts.stt_sec", "s"),
            number("ai.timeout.tts_sec", 4, 15, "15", "timeouts.tts_sec", "s"),
            number("ai.timeout.llm_job_sec", 60, 140, "120", "timeouts.llm_job_sec", "s"),
            new Rule("ai.interview_reasoning", "CHOICE", null, null, null, List.of("minimal", "low", "medium"), "minimal",
                    "interview_reasoning", null),
            number("ai.rag.top_k", 4, 12, "8", "rag.top_k", null),
            number("ai.rag.min_similarity", 0.3, 0.8, "0.45", "rag.min_similarity", null));

    public static final String EMBEDDING_MODEL = "ai.model.embedding";

    private AiSettings() {
    }

    public static Optional<Rule> rule(String key) {
        return RULES.stream().filter(r -> r.key().equals(key)).findFirst();
    }

    /** @return null when valid, else a message for the admin */
    public static String problem(String key, String value) {
        Rule r = rule(key).orElse(null);
        if (r == null) {
            return "Unknown AI setting " + key;
        }
        String v = value == null ? "" : value.trim();
        return switch (r.kind()) {
            case "MODEL" -> MODEL.matcher(v).matches() || (v.isEmpty() && key.endsWith("_fallback"))
                    ? null : key + " must be a model id such as " + r.defaultValue();
            case "VOICE" -> VOICE.matcher(v).matches() ? null : key + " must be a voice name such as Kore";
            case "CHOICE" -> r.choices().contains(v) ? null : key + " must be one of " + r.choices();
            default -> {
                try {
                    double d = Double.parseDouble(v);
                    yield d >= r.min() && d <= r.max() ? null : key + " must be between " + fmt(r.min()) + " and " + fmt(r.max());
                } catch (NumberFormatException e) {
                    yield key + " must be a number";
                }
            }
        };
    }

    /** The {@code options} object for the AI node from the stored values (missing → the default). */
    public static Map<String, Object> options(Function<String, Optional<String>> stored) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Rule r : RULES) {
            String v = stored.apply(r.key()).filter(s -> problem(r.key(), s) == null).orElse(r.defaultValue()).trim();
            Object value = "NUMBER".equals(r.kind()) ? (Object) Double.parseDouble(v) : v;
            String[] path = r.path().split("\\.");
            @SuppressWarnings("unchecked")
            Map<String, Object> node = path.length == 1 ? out
                    : (Map<String, Object>) out.computeIfAbsent(path[0], k -> new LinkedHashMap<String, Object>());
            node.put(path[path.length - 1], value);
        }
        return out;
    }

    private static Rule model(String key, String group, String def, String path) {
        return new Rule(key, "MODEL", group, null, null, null, def, path, null);
    }

    private static Rule number(String key, double min, double max, String def, String path, String unit) {
        return new Rule(key, "NUMBER", null, min, max, null, def, path, unit);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
