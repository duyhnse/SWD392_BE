package swd392.group6.AIVES.ai.node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import swd392.group6.AIVES.ai.AiJobPort;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.AiNodeProperties;
import swd392.group6.AIVES.ai.AiUnavailableException;
import swd392.group6.AIVES.ai.EmbeddingPort;
import swd392.group6.AIVES.ai.InterviewAiPort;
import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.ai.SynthesizedAudio;
import swd392.group6.AIVES.ai.TextToSpeechPort;
import swd392.group6.AIVES.ai.Transcript;
import swd392.group6.AIVES.common.Language;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HTTP adapter for every AI port, talking to the AI node over the tailnet ({@code application.ai.mode=node},
 * contract {@code SWD_Docs/api/ai-node.openapi.yaml}). Field names on the wire are snake_case.
 */
@Slf4j
public class AiNodeClient implements InterviewAiPort, SpeechToTextPort, TextToSpeechPort, EmbeddingPort, AiJobPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final RestClient answerHttp;
    private final int dimension;

    public AiNodeClient(AiNodeProperties properties, int dimension) {
        this.http = client(properties, Duration.ofSeconds(15));
        this.answerHttp = client(properties, properties.processAnswerTimeout());
        this.dimension = dimension;
    }

    private static RestClient client(AiNodeProperties p, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(p.connectTimeout());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(p.baseUrl()).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + p.token()).build();
    }

    // ---- interview (16 §3.2) --------------------------------------------------------------------------------

    @Override
    public AnswerResult processAnswer(AnswerRequest r) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("request_id", r.requestId());
        context.put("language", r.language().name());
        context.put("hotwords", r.hotwords() == null ? List.of() : r.hotwords());
        context.put("main_question", r.mainQuestion());
        context.put("reference_answer", r.referenceAnswer());
        context.put("prior_turns", r.priorTurns() == null ? List.of() : r.priorTurns().stream()
                .map(t -> Map.of("type", t.type(), "question_text", t.questionText(),
                        "transcript", t.transcript() == null ? "" : t.transcript())).toList());
        context.put("current_question_text", r.currentQuestionText());
        context.put("followups_used", r.followupsUsed());
        context.put("max_followups", r.maxFollowups());
        context.put("want_follow_up_audio", r.wantFollowUpAudio());
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", filePart(r.audio(), r.audioContentType(), "answer"));
        form.add("context", jsonPart(context));
        JsonNode body = call(() -> answerHttp.post().uri("/v1/interview/process-answer")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class));
        JsonNode t = body.path("transcript");
        Transcript transcript = new Transcript(t.path("text").asString(""), t.path("model").asString(null),
                t.path("latency_ms").asLong(0));
        JsonNode a = body.path("analysis");
        Analysis analysis = a.isMissingNode() || a.isNull() ? null : analysis(a);
        SynthesizedAudio audio = audio(body.path("follow_up_audio"));
        String error = body.path("analysis_error").isMissingNode() || body.path("analysis_error").isNull()
                ? null : body.path("analysis_error").path("code").asString("ANALYSIS_FAILED");
        return new AnswerResult(transcript, analysis, audio, error);
    }

    private static Analysis analysis(JsonNode a) {
        JsonNode flags = a.path("flags");
        List<SelfCorrection> corrections = new ArrayList<>();
        flags.path("self_corrections").forEach(c -> corrections.add(
                new SelfCorrection(c.path("retracted").asString(""), c.path("corrected").asString(""))));
        return new Analysis(a.path("coverage").asString("PARTIAL"), strings(a.path("covered_points")),
                strings(a.path("missing_points")), flags.path("is_vague").asBoolean(false),
                flags.path("is_contradictory").asBoolean(false), flags.path("is_bluffing").asBoolean(false),
                flags.path("is_off_topic_or_wrong").asBoolean(false), flags.path("keyword_stuffing_score").asDouble(0),
                corrections, a.path("follow_up_question").isNull() ? null : a.path("follow_up_question").asString(null),
                a.path("rationale").asString(""), a.path("model").asString(null));
    }

    // ---- speech (16 §3.3, §3.4) ------------------------------------------------------------------------------

    @Override
    public Transcript transcribe(SpeechToTextRequest r) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", filePart(r.audio(), r.contentType(), "answer"));
        form.add("context", jsonPart(Map.of("language", r.language().name(),
                "prompt", r.hotwordPrompt() == null ? "" : r.hotwordPrompt())));
        JsonNode body = call(() -> http.post().uri("/v1/stt").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form).retrieve().body(JsonNode.class));
        return new Transcript(body.path("text").asString(""), body.path("model").asString(null),
                body.path("latency_ms").asLong(0));
    }

    @Override
    public SynthesizedAudio synthesize(String text, Language language) {
        JsonNode body = call(() -> http.post().uri("/v1/tts").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text, "language", language.name(), "format", "mp3")).retrieve().body(JsonNode.class));
        SynthesizedAudio audio = audio(body);
        if (audio == null) {
            throw new AiUnavailableException("TTS returned no audio");
        }
        return audio;
    }

    // ---- embeddings (16 §3.5) --------------------------------------------------------------------------------

    @Override
    public List<float[]> embed(List<String> texts) {
        JsonNode body = call(() -> http.post().uri("/v1/embeddings").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("inputs", texts, "purpose", "DOCUMENT")).retrieve().body(JsonNode.class));
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode v : body.path("vectors")) {
            float[] f = new float[v.size()];
            for (int i = 0; i < f.length; i++) {
                f[i] = (float) v.get(i).asDouble();
            }
            if (f.length != dimension) {
                throw new AiUnavailableException("Embedding dimension " + f.length + " ≠ configured " + dimension);
            }
            vectors.add(f);
        }
        return vectors;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    // ---- jobs (16 §4) ----------------------------------------------------------------------------------------

    @Override
    public Optional<AiJobOutcome> submit(UUID jobId, AiJobType type, JsonNode input, String callbackUrl) {
        Map<String, Object> body = Map.of("job_id", jobId.toString(), "type", type.name(), "callback_url", callbackUrl,
                "input", input);
        call(() -> http.post().uri("/v1/jobs").contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .toBodilessEntity());
        return Optional.empty();
    }

    @Override
    public Optional<AiJobOutcome> poll(UUID jobId) {
        JsonNode body;
        try {
            body = http.get().uri("/v1/jobs/{id}", jobId).retrieve().body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.of(new AiJobOutcome("FAILED", null, "AI_JOB_LOST", "The AI node does not know this job"));
            }
            throw new AiUnavailableException("Polling failed: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new AiUnavailableException("Polling failed: " + e.getMessage(), e);
        }
        String status = body.path("status").asString("");
        if (!status.equals("SUCCEEDED") && !status.equals("FAILED")) {
            return Optional.empty();
        }
        JsonNode error = body.path("error");
        return Optional.of(new AiJobOutcome(status, status.equals("SUCCEEDED") ? body.path("result") : null,
                error.path("code").asString(null), error.path("message").asString(null)));
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static <T> T call(Supplier<T> request) {
        try {
            T result = request.get();
            if (result == null) {
                throw new AiUnavailableException("Empty response from the AI node");
            }
            return result;
        } catch (RestClientException e) {
            throw new AiUnavailableException("AI node call failed: " + e.getMessage(), e);
        }
    }

    private static SynthesizedAudio audio(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.path("audio_base64").asString("").isEmpty()) {
            return null;
        }
        return new SynthesizedAudio(Base64.getDecoder().decode(node.path("audio_base64").asString()),
                node.path("content_type").asString("audio/mpeg"));
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString("")));
        return out;
    }

    private static HttpEntity<ByteArrayResource> filePart(byte[] data, String contentType, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType));
        String ext = contentType != null && contentType.toLowerCase(Locale.ROOT).contains("mp4") ? "m4a" : "webm";
        return new HttpEntity<>(new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return name + "." + ext;
            }
        }, headers);
    }

    private static HttpEntity<String> jsonPart(Object value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(JSON.writeValueAsString(value), headers);
    }
}
