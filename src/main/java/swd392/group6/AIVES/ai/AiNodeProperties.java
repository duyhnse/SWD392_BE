package swd392.group6.AIVES.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code application.ai-node.*} — how node 1 reaches the AI node over Tailscale and how the node calls back (16 §2).
 *
 * @param baseUrl         e.g. {@code http://aives-ai:8000} or {@code http://100.x.y.z:8000} over Tailscale; blank = AI unavailable
 * @param token           bearer token node 1 → AI node
 * @param callbackSecret  HMAC key the AI node signs callbacks with (and node 1 signs file links with); blank = a random
 *                        key for this process (links still work; callbacks from a node need the shared value)
 * @param callbackBaseUrl how the AI node reaches node 1, e.g. {@code http://aives-web:8081}
 * @param jobTimeout      a SUBMITTED job without result after this is polled, then TIMED_OUT after 3× this
 */
@ConfigurationProperties("application.ai-node")
public record AiNodeProperties(String baseUrl, String token, String callbackSecret, String callbackBaseUrl,
                               Duration connectTimeout, Duration processAnswerTimeout, Duration jobTimeout) {

    public AiNodeProperties {
        // Without a configured secret use a random one for this process, so making a signed file link never fails.
        if (callbackSecret == null || callbackSecret.isBlank()) {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
            callbackSecret = java.util.HexFormat.of().formatHex(random);
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        processAnswerTimeout = processAnswerTimeout == null ? Duration.ofSeconds(12) : processAnswerTimeout;
        jobTimeout = jobTimeout == null ? Duration.ofMinutes(5) : jobTimeout;
    }

    public String callbackUrl(java.util.UUID jobId) {
        String base = callbackBaseUrl == null || callbackBaseUrl.isBlank() ? "http://localhost:8080" : callbackBaseUrl;
        return base.replaceAll("/+$", "") + "/internal/ai-node/jobs/" + jobId + "/result";
    }
}
