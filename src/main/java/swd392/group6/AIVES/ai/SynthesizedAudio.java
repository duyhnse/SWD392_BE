package swd392.group6.AIVES.ai;

/** @param durationMs playback length when the AI node reports it (null = unknown) */
public record SynthesizedAudio(byte[] data, String contentType, Integer durationMs) {
}
