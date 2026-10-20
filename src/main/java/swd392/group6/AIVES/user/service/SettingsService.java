package swd392.group6.AIVES.user.service;

import swd392.group6.AIVES.user.dto.UpdateSettingsRequest;

import swd392.group6.AIVES.user.dto.SettingResponse;

import swd392.group6.AIVES.user.SettingsApi;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.ai.AiEmbeddingModelChangedEvent;
import swd392.group6.AIVES.ai.AiSettings;
import swd392.group6.AIVES.ai.AiSettingsSource;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** FG7 language & speech configuration stored in {@code system_settings} (15 §4, §5.1). */
@Service
@RequiredArgsConstructor
public class SettingsService implements SettingsApi, AiSettingsSource {

    /** Business settings, plus the admin's AI settings {@code ai.*} sent to the AI node with every call (D70). */
    static final Set<String> KNOWN_KEYS = java.util.stream.Stream.concat(Stream.of("default_language",
                    "exam.seconds.REMEMBER", "exam.seconds.UNDERSTAND", "exam.seconds.APPLY", "exam.seconds.ANALYZE",
                    "exam.seconds.ANY", "account.default_password", "retention.recording_days"), AiSettings.RULES.stream().map(AiSettings.Rule::key))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        return jdbc.sql("select setting_key, setting_value::text, description, updated_at from system_settings order by setting_key")
                .query((rs, i) -> new SettingResponse(rs.getString(1), JSON.readTree(rs.getString(2)), rs.getString(3),
                        rs.getTimestamp(4).toInstant(), AiSettings.rule(rs.getString(1)).orElse(null)))
                .list();
    }

    /** Upserts the given keys; every key must be known and every value a JSON string (422 otherwise). */
    @Transactional
    public List<SettingResponse> update(UpdateSettingsRequest request, UUID adminId) {
        if (request == null || request.settings() == null || request.settings().isEmpty()) {
            throw ApiException.unprocessable("SETTINGS_EMPTY", "Send at least one setting: {\"settings\": {key: value}}");
        }
        request.settings().forEach(SettingsService::validate);
        String oldEmbedding = value(AiSettings.EMBEDDING_MODEL).orElse(null);
        Timestamp now = Timestamp.from(clock.instant());
        request.settings().forEach((key, value) -> jdbc.sql("""
                        insert into system_settings (setting_key, setting_value, updated_by, updated_at)
                        values (?, cast(? as jsonb), ?, ?)
                        on conflict (setting_key) do update
                          set setting_value = excluded.setting_value, updated_by = excluded.updated_by,
                              updated_at = excluded.updated_at""")
                .params(key, JSON.writeValueAsString(value), adminId, now)
                .update());
        JsonNode embedding = request.settings().get(AiSettings.EMBEDDING_MODEL);
        if (embedding != null && !embedding.asString().trim().equals(oldEmbedding)) {
            events.publishEvent(new AiEmbeddingModelChangedEvent(embedding.asString().trim())); // → re-index materials
        }
        return list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> value(String key) {
        return jdbc.sql("select setting_value::text from system_settings where setting_key = ?").param(key)
                .query(String.class).optional()
                .map(JSON::readTree)
                .filter(JsonNode::isString)
                .map(JsonNode::asString);
    }

    /** Language for new courses when the admin does not choose one. */
    @Transactional(readOnly = true)
    public Language defaultLanguage() {
        return jdbc.sql("select setting_value::text from system_settings where setting_key = 'default_language'")
                .query(String.class).optional()
                .map(JSON::readTree)
                .filter(JsonNode::isString)
                .map(node -> {
                    try {
                        return Language.valueOf(node.asString());
                    } catch (IllegalArgumentException e) {
                        return Language.VI;
                    }
                })
                .orElse(Language.VI);
    }

    /** D62: initial password of accounts created by an admin / import when none is given. */
    String defaultPassword() {
        return value("account.default_password").filter(v -> v.length() >= PasswordRules.MIN_PASSWORD).orElse("Aives@2026");
    }

    private static void validate(String key, JsonNode value) {
        if (!KNOWN_KEYS.contains(key)) {
            throw ApiException.unprocessable("UNKNOWN_SETTING", "Unknown setting: " + key);
        }
        if (value == null || !value.isString()) {
            throw ApiException.unprocessable("INVALID_SETTING_VALUE", "Setting " + key + " must be a string");
        }
        if (key.startsWith("ai.")) {
            String problem = AiSettings.problem(key, value.asString());
            if (problem != null) {
                throw ApiException.unprocessable("INVALID_SETTING_VALUE", problem);
            }
        }
        if (key.equals("default_language") && !Set.of("VI", "EN").contains(value.asString())) {
            throw ApiException.unprocessable("INVALID_SETTING_VALUE", "default_language must be VI or EN");
        }
        if (key.equals("retention.recording_days")) {
            int days;
            try {
                days = Integer.parseInt(value.asString().trim());
            } catch (NumberFormatException e) {
                days = -1;
            }
            if (days < 30 || days > 3650) {
                throw ApiException.unprocessable("INVALID_SETTING_VALUE", "retention.recording_days must be 30–3650 days");
            }
        }
        if (key.equals("account.default_password")) {
            String p = value.asString();
            if (p.length() < PasswordRules.MIN_PASSWORD || p.length() > PasswordRules.MAX_PASSWORD) {
                throw ApiException.unprocessable("INVALID_SETTING_VALUE", "The default password must be 8–100 characters");
            }
        }
        if (key.startsWith("exam.seconds.")) {
            int seconds;
            try {
                seconds = Integer.parseInt(value.asString().trim());
            } catch (NumberFormatException e) {
                seconds = -1;
            }
            if (seconds < 30 || seconds > 1800) {
                throw ApiException.unprocessable("INVALID_SETTING_VALUE", key + " must be a number of seconds, 30–1800");
            }
        }
    }
}
