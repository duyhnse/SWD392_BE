package swd392.group6.AIVES.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Downloads the Google profile photo named in the ID token's "picture" claim. Only https URLs on
 * Google's photo host are fetched (no request to arbitrary hosts), with a short timeout and a size cap.
 * Failure is never fatal: the account is simply linked without a picture.
 */
@Slf4j
@Component
class GoogleAvatarFetcher {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    Optional<byte[]> fetch(String pictureUrl) {
        URI uri = allowed(pictureUrl);
        if (uri == null) {
            return Optional.empty();
        }
        try {
            HttpResponse<InputStream> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    return Optional.empty();
                }
                byte[] data = body.readNBytes(MAX_BYTES + 1);
                return data.length == 0 || data.length > MAX_BYTES ? Optional.empty() : Optional.of(data);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("Could not download the Google profile photo: {}", e.toString());
            return Optional.empty();
        }
    }

    /** https + *.googleusercontent.com only; asks Google for a 512 px version ("=s96-c" → "=s512-c"). */
    static URI allowed(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !host.endsWith(".googleusercontent.com")) {
                return null;
            }
            return URI.create(uri.toString().replaceFirst("=s\\d+(-c)?$", "=s512-c"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
