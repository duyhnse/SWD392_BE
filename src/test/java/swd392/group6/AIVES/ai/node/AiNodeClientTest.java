package swd392.group6.AIVES.ai.node;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.ai.AiJobPort.AiJobOutcome;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.ai.AiNodeProperties;
import swd392.group6.AIVES.ai.AiUnavailableException;
import swd392.group6.AIVES.ai.InterviewAiPort.AnswerRequest;
import swd392.group6.AIVES.ai.InterviewAiPort.AnswerResult;
import swd392.group6.AIVES.ai.InterviewAiPort.PriorTurn;
import swd392.group6.AIVES.common.Language;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The node-1 side of the AI node contract (SWD_Docs api/ai-node.openapi.yaml) against a fake node. */
class AiNodeClientTest {

    private HttpServer server;
    private final Map<String, String> requests = new ConcurrentHashMap<>();
    private final Map<String, String> authHeaders = new ConcurrentHashMap<>();
    private final Map<String, Object[]> responses = new ConcurrentHashMap<>();
    private AiNodeClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            requests.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.put(key, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            Object[] response = responses.getOrDefault(key, new Object[]{404, "{\"code\":\"NOT_FOUND\"}"});
            byte[] body = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders((int) response[0], body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        client = new AiNodeClient(new AiNodeProperties("http://127.0.0.1:" + server.getAddress().getPort(), "node-token",
                "secret", "http://node1:8081", Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofMinutes(1)), 3);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void processAnswerSendsAudioAndContextAndReadsTheProposal() {
        String audio = Base64.getEncoder().encodeToString(new byte[] {9, 9});
        responses.put("POST /v1/interview/process-answer", new Object[]{200, """
                {"transcript":{"text":"em nghĩ là","language":"VI","model":"whisper","latency_ms":900},
                 "analysis":{"coverage":"PARTIAL","covered_points":[],"missing_points":["A"],
                   "flags":{"is_vague":true,"is_contradictory":false,"is_bluffing":false,"is_off_topic_or_wrong":false,
                            "keyword_stuffing_score":0.7,"self_corrections":[{"retracted":"REST","corrected":"gRPC"}]},
                   "follow_up_question":"Vì sao?","rationale":"thiếu ý A","model":"gpt-x"},
                 "follow_up_audio":{"audio_base64":"%s","content_type":"audio/mpeg","duration_ms":800}}""".formatted(audio)});

        AnswerResult r = client.processAnswer(new AnswerRequest("turn-7", Language.VI, new byte[] {1, 2, 3}, "audio/webm",
                List.of("SOLID"), "Main?", "- A", List.of(new PriorTurn("MAIN", "Main?", "trước")), "Main?", 0, 2, true, null));

        assertThat(r.transcript().text()).isEqualTo("em nghĩ là");
        assertThat(r.transcript().latencyMs()).isEqualTo(900);
        assertThat(r.analysis().vague()).isTrue();
        assertThat(r.analysis().keywordStuffingScore()).isEqualTo(0.7);
        assertThat(r.analysis().selfCorrections()).singleElement()
                .satisfies(c -> assertThat(c.corrected()).isEqualTo("gRPC"));
        assertThat(r.analysis().followUpQuestion()).isEqualTo("Vì sao?");
        assertThat(r.followUpAudio().data()).containsExactly(9, 9);
        assertThat(r.analysisError()).isNull();
        String sent = requests.get("POST /v1/interview/process-answer");
        assertThat(sent).contains("name=\"audio\"", "name=\"context\"", "\"request_id\":\"turn-7\"", "\"max_followups\":2",
                "\"prior_turns\":[{");
        assertThat(authHeaders.get("POST /v1/interview/process-answer")).isEqualTo("Bearer node-token");
    }

    @Test
    void analysisFailureStillReturnsTheTranscript() {
        responses.put("POST /v1/interview/process-answer", new Object[]{200, """
                {"transcript":{"text":"abc"},"analysis":null,"analysis_error":{"code":"LLM_TIMEOUT","message":"8s"}}"""});
        AnswerResult r = client.processAnswer(new AnswerRequest("t", Language.EN, new byte[] {1}, "audio/webm", List.of(),
                "Q", "", List.of(), "Q", 0, 1, false, null));
        assertThat(r.analysis()).isNull();
        assertThat(r.analysisError()).isEqualTo("LLM_TIMEOUT");
        assertThat(r.transcript().text()).isEqualTo("abc");
    }

    @Test
    void nodeErrorsBecomeAiUnavailable() {
        responses.put("POST /v1/tts", new Object[]{503, "{\"code\":\"AI_PROVIDER_UNAVAILABLE\"}"});
        assertThatThrownBy(() -> client.synthesize("Xin chào", Language.VI)).isInstanceOf(AiUnavailableException.class);
        responses.put("POST /v1/embeddings", new Object[]{200, "{\"vectors\":[[0.1,0.2]]}"});
        assertThatThrownBy(() -> client.embed(List.of("x"))).isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    void jobsAreSubmittedAndPolled() {
        UUID jobId = UUID.randomUUID();
        responses.put("POST /v1/jobs", new Object[]{202, "{\"job_id\":\"" + jobId + "\",\"status\":\"QUEUED\"}"});
        Optional<AiJobOutcome> immediate = client.submit(jobId, AiJobType.GRADE_THREAD,
                JsonMapper.builder().build().readTree("{\"turns\":[]}"), "http://node1:8081/internal/ai-node/jobs/x/result");
        assertThat(immediate).isEmpty();
        assertThat(requests.get("POST /v1/jobs")).contains("\"type\":\"GRADE_THREAD\"", "\"callback_url\"", "\"input\":{");

        responses.put("GET /v1/jobs/" + jobId, new Object[]{200, "{\"status\":\"RUNNING\"}"});
        assertThat(client.poll(jobId)).isEmpty();
        responses.put("GET /v1/jobs/" + jobId, new Object[]{200, "{\"status\":\"SUCCEEDED\",\"result\":{\"feedback\":\"ok\"}}"});
        assertThat(client.poll(jobId)).hasValueSatisfying(o -> {
            assertThat(o.succeeded()).isTrue();
            assertThat(o.result().path("feedback").asString()).isEqualTo("ok");
        });
        assertThat(client.poll(UUID.randomUUID())).hasValueSatisfying(o -> assertThat(o.errorCode()).isEqualTo("AI_JOB_LOST"));
    }
}
