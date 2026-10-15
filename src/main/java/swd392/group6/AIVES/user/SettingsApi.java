package swd392.group6.AIVES.user;

import java.util.Optional;

/** Read access to {@code system_settings} for other modules (values are strings, 15 §8.1). */
public interface SettingsApi {

    Optional<String> value(String key);
}
