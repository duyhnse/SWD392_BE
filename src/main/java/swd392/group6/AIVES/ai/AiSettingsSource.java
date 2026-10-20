package swd392.group6.AIVES.ai;

import java.util.Optional;

/** Where the admin's {@code ai.*} settings are stored (the user module's system settings, D70). */
public interface AiSettingsSource {

    Optional<String> value(String key);
}
