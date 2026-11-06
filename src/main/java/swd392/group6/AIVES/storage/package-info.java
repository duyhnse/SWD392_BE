/**
 * Object storage port (course materials, TTS audio, answer recordings) — 02_ARCHITECTURE.md §2, 03_DATA_MODEL.md §6.
 * {@code application.storage.provider}: {@code local} (default, files under {@code local-dir}) or {@code memory} (tests).
 * The MinIO/S3 adapter arrives with the real AI adapters (M3).
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package swd392.group6.AIVES.storage;
