package swd392.group6.AIVES.ai;

public interface LlmPort {

    /** @throws AiUnavailableException on timeout, provider error or empty output */
    LlmResponse complete(LlmRequest request);
}
