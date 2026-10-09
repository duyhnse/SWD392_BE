package swd392.group6.AIVES.ai;

import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

/** Transport of asynchronous jobs to the AI node (contract 16 §4). Business modules use {@link AiJobService}. */
public interface AiJobPort {

    /**
     * Hands the job over. The node answers later on the callback URL; an adapter that can finish at once (mock)
     * returns the outcome directly.
     *
     * @throws AiUnavailableException when the node cannot accept the job now (the sweeper retries)
     */
    Optional<AiJobOutcome> submit(UUID jobId, AiJobType type, JsonNode input, String callbackUrl);

    /** Fallback when a callback is missing: the job's current state on the node, empty while still running. */
    Optional<AiJobOutcome> poll(UUID jobId);

    /** @param result {@code status = SUCCEEDED}: the type's result object; otherwise null */
    record AiJobOutcome(String status, JsonNode result, String errorCode, String errorMessage) {

        public boolean succeeded() {
            return "SUCCEEDED".equals(status);
        }
    }
}
