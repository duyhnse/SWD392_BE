/**
 * Ports to the AI node (answer processing, AI jobs, embeddings, speech-to-text, text-to-speech) — 02 §2, 16.
 * <p>
 * Business modules depend only on the interfaces in this package, never on a provider SDK.
 * {@code application.ai.mode=mock} (default) wires the deterministic adapters in {@code ai.mock},
 * so the whole system runs with no AI node; {@code node} wires {@code ai.node.AiNodeClient} (D41, 16_AI_NODE_CONTRACT).
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package swd392.group6.AIVES.ai;
