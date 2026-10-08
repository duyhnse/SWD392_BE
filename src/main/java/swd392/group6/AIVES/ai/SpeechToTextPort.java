package swd392.group6.AIVES.ai;

public interface SpeechToTextPort {

    /** @throws AiUnavailableException on timeout or provider error (caller retries once — WF3 EX-4) */
    Transcript transcribe(SpeechToTextRequest request);
}
