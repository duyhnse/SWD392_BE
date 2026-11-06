package swd392.group6.AIVES.ai.node;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** HMAC callbacks between the nodes (16 §2.3). */
class AiNodeSignatureTest {

    private static final byte[] BODY = "{\"job_id\":\"x\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2031-03-03T08:00:00Z");

    @Test
    void validSignatureWithinFiveMinutesIsAccepted() {
        long ts = NOW.getEpochSecond() - 60;
        String sig = AiNodeSignature.sign("secret", ts, BODY);
        assertThat(sig).startsWith("v1=").hasSize(3 + 64);
        assertThat(AiNodeSignature.verify("secret", String.valueOf(ts), sig, BODY, NOW)).isTrue();
    }

    @Test
    void wrongSecretTamperedBodyOldTimestampOrMissingPartsAreRejected() {
        long ts = NOW.getEpochSecond();
        String sig = AiNodeSignature.sign("secret", ts, BODY);
        assertThat(AiNodeSignature.verify("other", String.valueOf(ts), sig, BODY, NOW)).isFalse();
        assertThat(AiNodeSignature.verify("secret", String.valueOf(ts), sig, "{}".getBytes(StandardCharsets.UTF_8), NOW)).isFalse();
        long old = NOW.getEpochSecond() - 301;
        assertThat(AiNodeSignature.verify("secret", String.valueOf(old), AiNodeSignature.sign("secret", old, BODY), BODY, NOW))
                .isFalse();
        assertThat(AiNodeSignature.verify("secret", null, sig, BODY, NOW)).isFalse();
        assertThat(AiNodeSignature.verify("", String.valueOf(ts), sig, BODY, NOW)).isFalse();
        assertThat(AiNodeSignature.verify("secret", "abc", sig, BODY, NOW)).isFalse();
    }
}
