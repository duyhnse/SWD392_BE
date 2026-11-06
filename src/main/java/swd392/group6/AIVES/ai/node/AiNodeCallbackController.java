package swd392.group6.AIVES.ai.node;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.ai.AiJobPort.AiJobOutcome;
import swd392.group6.AIVES.ai.AiJobService;
import swd392.group6.AIVES.ai.AiNodeProperties;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.storage.StoredObject;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Endpoints the AI node calls on node 1 (contract 16 §5). Not behind JWT: every request is authenticated by an
 * HMAC signature (callbacks) or a signed, expiring link (files). Reachable only over the tailnet — the public Caddy
 * sites answer 404 for {@code /internal/*}.
 */
@RestController
@RequestMapping("/internal/ai-node")
@RequiredArgsConstructor
class AiNodeCallbackController {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> FINAL = Set.of("SUCCEEDED", "FAILED");

    private final AiJobService jobs;
    private final AiNodeProperties properties;
    private final AiNodeFileLinks fileLinks;
    private final StoragePort storage;
    private final Clock clock;

    /** Body: {@code {job_id, type, status: SUCCEEDED|FAILED, result?, error?: {code, message}}} (16 §4.3). */
    @PostMapping(path = "/jobs/{jobId}/result", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> result(@PathVariable UUID jobId,
                                @RequestHeader(name = AiNodeSignature.TIMESTAMP_HEADER, required = false) String timestamp,
                                @RequestHeader(name = AiNodeSignature.SIGNATURE_HEADER, required = false) String signature,
                                @RequestBody byte[] body) {
        if (!AiNodeSignature.verify(properties.callbackSecret(), timestamp, signature, body, clock.instant())) {
            throw ApiException.unauthorized("INVALID_SIGNATURE", "Missing or invalid callback signature");
        }
        JsonNode payload;
        try {
            payload = JSON.readTree(body);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BODY", "The callback body is not JSON");
        }
        String status = payload.path("status").asString("");
        if (!FINAL.contains(status)) {
            throw ApiException.unprocessable("INVALID_STATUS", "status must be SUCCEEDED or FAILED");
        }
        if (!payload.path("job_id").isMissingNode() && !jobId.toString().equals(payload.path("job_id").asString(""))) {
            throw ApiException.unprocessable("JOB_ID_MISMATCH", "job_id in the body differs from the URL");
        }
        if (jobs.find(jobId).isEmpty()) {
            throw ApiException.notFound("AI_JOB_NOT_FOUND", "Unknown job");
        }
        JsonNode error = payload.path("error");
        jobs.complete(jobId, new AiJobOutcome(status, "SUCCEEDED".equals(status) ? payload.path("result") : null,
                error.path("code").asString(null), error.path("message").asString(null)));
        // 204 also for a repeated callback: the first result wins and the node can stop retrying.
        return ResponseEntity.noContent().build();
    }

    /** Material download for INDEX_MATERIAL jobs; the link expires after 15 minutes (16 §5.2). */
    @GetMapping("/files")
    ResponseEntity<byte[]> file(@RequestParam("key") String key, @RequestParam("expires") long expires,
                                @RequestParam("sig") String sig) {
        if (!fileLinks.valid(key, expires, sig)) {
            throw ApiException.notFound("FILE_NOT_FOUND", "File not found");
        }
        StoredObject object = storage.get(key).orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "File not found"));
        String type = object.contentType() == null || object.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : object.contentType();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).cacheControl(CacheControl.noStore())
                .body(object.data());
    }
}
