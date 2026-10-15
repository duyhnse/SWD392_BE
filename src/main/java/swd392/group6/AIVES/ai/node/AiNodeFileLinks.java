package swd392.group6.AIVES.ai.node;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import swd392.group6.AIVES.ai.AiNodeProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;

/** Short-lived signed links the AI node uses to download a stored file from node 1 (16 §5.2). */
@Component
@RequiredArgsConstructor
public class AiNodeFileLinks {

    static final Duration TTL = Duration.ofMinutes(15);

    private final AiNodeProperties properties;
    private final Clock clock;

    public String create(String storageKey) {
        long expires = clock.instant().plus(TTL).getEpochSecond();
        String base = properties.callbackBaseUrl() == null || properties.callbackBaseUrl().isBlank()
                ? "http://localhost:8080" : properties.callbackBaseUrl().replaceAll("/+$", "");
        return UriComponentsBuilder.fromUriString(base + "/internal/ai-node/files")
                .queryParam("key", storageKey).queryParam("expires", expires)
                .queryParam("sig", signature(storageKey, expires)).encode().toUriString();
    }

    boolean valid(String storageKey, long expires, String sig) {
        if (properties.callbackSecret() == null || properties.callbackSecret().isBlank()
                || clock.instant().getEpochSecond() > expires) {
            return false;
        }
        return MessageDigest.isEqual(signature(storageKey, expires).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    private String signature(String storageKey, long expires) {
        return AiNodeSignature.sign(properties.callbackSecret(), expires, storageKey.getBytes(StandardCharsets.UTF_8))
                .substring(3);
    }
}
