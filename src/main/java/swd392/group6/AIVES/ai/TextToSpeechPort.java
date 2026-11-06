package swd392.group6.AIVES.ai;

import swd392.group6.AIVES.common.Language;

public interface TextToSpeechPort {

    /** @throws AiUnavailableException on failure — caller falls back to browser speechSynthesis (WF3 EX-7) */
    SynthesizedAudio synthesize(String text, Language language);
}
