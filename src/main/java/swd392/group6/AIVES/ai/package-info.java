/**
 * Ports to external AI services (LLM, embeddings, speech-to-text, text-to-speech) — 02_ARCHITECTURE.md §2, §5.
 * <p>
 * Business modules depend only on the interfaces in this package, never on a provider SDK.
 * {@code application.ai.mode=mock} (default) wires the deterministic adapters in {@code ai.mock},
 * so the whole system runs with no API keys. Real adapters arrive in M3 (AIV-18).
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package swd392.group6.AIVES.ai;
