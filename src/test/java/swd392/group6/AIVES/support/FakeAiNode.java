package swd392.group6.AIVES.support;

import org.springframework.boot.test.context.TestConfiguration;
import swd392.group6.AIVES.ai.AiJobPort;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.AiMaterialIndexPort;
import swd392.group6.AIVES.ai.AiUnavailableException;
import swd392.group6.AIVES.ai.InterviewAiPort;
import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.ai.SynthesizedAudio;
import swd392.group6.AIVES.ai.TextToSpeechPort;
import swd392.group6.AIVES.ai.Transcript;
import swd392.group6.AIVES.common.Language;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
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
public class FakeAiNode implements AiJobPort, AiMaterialIndexPort, InterviewAiPort, SpeechToTextPort, TextToSpeechPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    public final List<UUID> deletedMaterials = new CopyOnWriteArrayList<>();
    /** Requests seen by process-answer (WF3 tests check what was skipped, e.g. AC-I5). */
    public final List<AnswerRequest> answerRequests = new CopyOnWriteArrayList<>();
    public volatile boolean ttsBroken;
    /** The AI node answers a follow-up without its voice (it ran out of turn time, D70). */
    public volatile boolean followUpVoiceLate;
    public final List<String> spoken = new CopyOnWriteArrayList<>();

    // ---- interview (16 §3): the answer clip is plain UTF-8 text = the transcript --------------------------------
    // "#stt-fail": no transcript at all · "#ai-fail": process-answer fails, STT alone works · "không biết": wrong ·
    // fewer than 15 words: partial with a follow-up · otherwise complete.

    @Override
    public AnswerResult processAnswer(AnswerRequest request) {
        answerRequests.add(request);
        String text = new String(request.audio(), StandardCharsets.UTF_8);
        if (text.contains("#stt-fail") || text.contains("#ai-fail")) {
            throw new AiUnavailableException("fake AI node: process-answer down");
        }
        Transcript transcript = new Transcript(text, "fake-stt", 5);
        Analysis analysis;
        boolean shortAnswer = text.trim().split("\\s+").length < 15;
        if (text.contains("không biết")) {
            analysis = new Analysis("INSUFFICIENT", List.of(), List.of("ý 1"), false, false, false, true, 0, List.of(),
                    null, "fake: does not know", "fake-llm");
        } else if (shortAnswer) {
            analysis = new Analysis("PARTIAL", List.of(), List.of("ý 1"), false, false, false, false, 0, List.of(),
                    "Em giải thích rõ hơn ý 1 được không?", "fake: short", "fake-llm");
        } else {
            analysis = new Analysis("COMPLETE", List.of("ý 1"), List.of(), false, false, false, false, 0, List.of(),
                    null, "fake: complete", "fake-llm");
        }
        SynthesizedAudio audio = analysis.followUpQuestion() == null || ttsBroken || followUpVoiceLate ? null
                : new SynthesizedAudio("fake follow-up audio".getBytes(StandardCharsets.UTF_8), "audio/mpeg", 1500);
        return new AnswerResult(transcript, analysis, audio, null);
    }

    @Override
    public Transcript transcribe(SpeechToTextRequest request) {
        String text = new String(request.audio(), StandardCharsets.UTF_8);
        if (text.contains("#stt-fail")) {
            throw new AiUnavailableException("fake AI node: STT down");
        }
        return new Transcript(text, "fake-stt", 5);
    }

    @Override
    public SynthesizedAudio synthesize(String text, Language language) {
        if (ttsBroken) {
            throw new AiUnavailableException("fake AI node: TTS down");
        }
        spoken.add(text);
        return new SynthesizedAudio(("tts:" + language + ":" + text).getBytes(StandardCharsets.UTF_8), "audio/mpeg", 2000);
    }

    @Override
    public Optional<AiJobOutcome> submit(UUID jobId, AiJobType type, JsonNode input, String callbackUrl) {
        return Optional.of(switch (type) {
            case INDEX_MATERIAL -> index(input);
            case GENERATE_QUESTIONS -> generate(input);
            case GRADE_THREAD -> grade(input);
            case SUMMARIZE_EVALUATION -> ok(Map.of("summary", "fake summary", "model", "fake"));
        });
    }

    /** P-GRADE stand-in: 3/4 of every criterion's max; a transcript with "#ai-fail" gets an answer without criteria. */
    public final List<JsonNode> gradeInputs = new CopyOnWriteArrayList<>();

    private AiJobOutcome grade(JsonNode input) {
        gradeInputs.add(input);
        boolean fail = false;
        for (JsonNode t : input.path("turns")) {
            fail |= t.path("transcript").asString("").contains("#ai-fail");
        }
        List<Map<String, Object>> criteria = new ArrayList<>();
        if (!fail) {
            for (JsonNode c : input.path("rubric").path("criteria")) {
                criteria.add(Map.of("criterion_id", c.path("criterion_id").asString(),
                        "score", c.path("max_score").asDouble() * 0.75, "justification", "fake: mostly covered"));
            }
        }
        return ok(Map.of("criteria", criteria, "strengths", List.of("fake strength"), "weaknesses", List.of("fake weakness"),
                "missing_points", List.of("fake missing point"), "feedback", "fake feedback", "model", "fake-llm"));
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
