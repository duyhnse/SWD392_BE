package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** FG7 language & speech configuration stored in {@code system_settings} (15 §4, §5.1). */
@Service
@RequiredArgsConstructor
class SettingsService {

    static final Set<String> KNOWN_KEYS = Set.of("default_language", "stt.provider", "stt.language.vi",
            "stt.language.en", "tts.provider", "tts.voice.vi", "tts.voice.en");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;
    private final Clock clock;

    record SettingResponse(String key, JsonNode value, String description, Instant updatedAt) {
    }

    record UpdateSettingsRequest(Map<String, JsonNode> settings) {
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> list() {
        return jdbc.sql("select setting_key, setting_value::text, description, updated_at from system_settings order by setting_key")
                .query((rs, i) -> new SettingResponse(rs.getString(1), JSON.readTree(rs.getString(2)), rs.getString(3),
                        rs.getTimestamp(4).toInstant()))
                .list();
    }

    /** Upserts the given keys; every key must be known and every value a JSON string (422 otherwise). */
    @Transactional
    public List<SettingResponse> update(UpdateSettingsRequest request, UUID adminId) {
        if (request == null || request.settings() == null || request.settings().isEmpty()) {
            throw ApiException.unprocessable("SETTINGS_EMPTY", "Send at least one setting: {\"settings\": {key: value}}");
        }
        request.settings().forEach(SettingsService::validate);
        Timestamp now = Timestamp.from(clock.instant());
        request.settings().forEach((key, value) -> jdbc.sql("""
                        insert into system_settings (setting_key, setting_value, updated_by, updated_at)
                        values (?, cast(? as jsonb), ?, ?)
                        on conflict (setting_key) do update
                          set setting_value = excluded.setting_value, updated_by = excluded.updated_by,
                              updated_at = excluded.updated_at""")
                .params(key, JSON.writeValueAsString(value), adminId, now)
                .update());
        return list();
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

    private static void validate(String key, JsonNode value) {
        if (!KNOWN_KEYS.contains(key)) {
            throw ApiException.unprocessable("UNKNOWN_SETTING", "Unknown setting: " + key);
        }
        if (value == null || !value.isString()) {
            throw ApiException.unprocessable("INVALID_SETTING_VALUE", "Setting " + key + " must be a string");
        }
        if (key.equals("default_language") && !Set.of("VI", "EN").contains(value.asString())) {
            throw ApiException.unprocessable("INVALID_SETTING_VALUE", "default_language must be VI or EN");
        }
    }
}
