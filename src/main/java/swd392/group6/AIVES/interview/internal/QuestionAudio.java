package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.ai.AiUnavailableException;
import swd392.group6.AIVES.ai.SynthesizedAudio;
import swd392.group6.AIVES.ai.TextToSpeechPort;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.storage.StoragePort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Spoken questions (TTS by the AI node), stored once per language + text: {@code tts/{sha256}.{ext}} plus a small
 * JSON with type and length. Main questions are asked by many students, so the cache is the latency lever of 05 §12.
 * Kept as evidence of what the student heard (FG5). Never called inside a DB transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class QuestionAudio {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> EXTENSIONS = Map.of("audio/mpeg", "mp3", "audio/wav", "wav",
            "audio/x-wav", "wav", "audio/ogg", "ogg", "audio/webm", "webm", "audio/mp4", "m4a");

    private final TextToSpeechPort tts;
    private final StoragePort storage;

    record Stored(String key, String contentType, Integer durationMs) {
    }

    /** Cached audio for the text, synthesised on a miss; empty when TTS fails (client speaks the text, EX-7). */
    Optional<Stored> forText(String text, Language language) {
        String base = "tts/" + hash(language + "|" + text);
        Optional<Stored> cached = cached(base);
        if (cached.isPresent()) {
            return cached;
        }
        try {
            return Optional.of(store(base, tts.synthesize(text, language)));
        } catch (AiUnavailableException | IllegalStateException e) {
            log.warn("TTS failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Stores audio the AI node already produced (a follow-up comes with its audio, 16 §3.1). */
    Optional<Stored> store(String text, Language language, SynthesizedAudio audio) {
        if (audio == null || audio.data() == null || audio.data().length == 0) {
            return forText(text, language);
        }
        String base = "tts/" + hash(language + "|" + text);
        return Optional.of(cached(base).orElseGet(() -> store(base, audio)));
    }

    private Optional<Stored> cached(String base) {
        return storage.get(base + ".json").map(meta -> {
            JsonNode n = JSON.readTree(new String(meta.data(), StandardCharsets.UTF_8));
            return new Stored(n.path("key").asString(), n.path("contentType").asString(),
                    n.path("durationMs").isNumber() ? n.path("durationMs").asInt() : null);
        }).filter(s -> storage.exists(s.key()));
    }

    private Stored store(String base, SynthesizedAudio audio) {
        String type = audio.contentType() == null ? "audio/mpeg" : audio.contentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        String key = base + "." + EXTENSIONS.getOrDefault(type, "bin");
        storage.put(key, audio.data(), type);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", key);
        meta.put("contentType", type);
        meta.put("durationMs", audio.durationMs());
        storage.put(base + ".json", JSON.writeValueAsBytes(meta), "application/json");
        return new Stored(key, type, audio.durationMs());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
