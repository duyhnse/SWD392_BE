/**
 * Node 1's side of the AI node contract (16): ports used by business modules (interview, AI jobs, material index) and
 * their only implementation, the HTTP client in {@code ai.node}, plus the signed callback / file-link endpoints.
 * Node 1 runs no AI itself — no models, no embeddings, no document parsing, no RAG (D63).
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package swd392.group6.AIVES.ai;
