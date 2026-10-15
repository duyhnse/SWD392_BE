package swd392.group6.AIVES.ai.node;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signatures between the nodes (contract 16 §2.3): {@code X-AIVES-Timestamp: <epoch seconds>} and
 * {@code X-AIVES-Signature: v1=<hex(HMAC(secret, timestamp + "." + body))>}; rejected when older than 5 minutes.
 */
public final class AiNodeSignature {

    public static final String TIMESTAMP_HEADER = "X-AIVES-Timestamp";
    public static final String SIGNATURE_HEADER = "X-AIVES-Signature";
    static final Duration MAX_SKEW = Duration.ofMinutes(5);

    private AiNodeSignature() {
    }

    public static String sign(String secret, long epochSeconds, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((epochSeconds + ".").getBytes(StandardCharsets.UTF_8));
            return "v1=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(String secret, String timestamp, String signature, byte[] body, Instant now) {
        if (secret == null || secret.isBlank() || timestamp == null || signature == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - ts) > MAX_SKEW.toSeconds()) {
            return false;
        }
        byte[] expected = sign(secret, ts, body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.trim().getBytes(StandardCharsets.UTF_8));
    }
}
