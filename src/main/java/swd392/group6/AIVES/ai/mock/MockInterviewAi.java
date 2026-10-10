package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.InterviewAiPort;
import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.ai.TextToSpeechPort;
import swd392.group6.AIVES.ai.Transcript;
import swd392.group6.AIVES.common.Language;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic answer analysis (02 §5): "không biết" → off-topic/wrong; fewer than 15 words → PARTIAL with a
 * follow-up about the first expected key point; otherwise COMPLETE. Uses the mock STT/TTS for the audio parts.
 */
public class MockInterviewAi implements InterviewAiPort {

    static final String MODEL = "mock-llm";
    private static final int SHORT_ANSWER_WORDS = 15;

    private final SpeechToTextPort stt;
    private final TextToSpeechPort tts;

    public MockInterviewAi(SpeechToTextPort stt, TextToSpeechPort tts) {
        this.stt = stt;
        this.tts = tts;
    }

    @Override
    public AnswerResult processAnswer(AnswerRequest r) {
        Transcript transcript = stt.transcribe(new SpeechToTextRequest(r.audio(), r.audioContentType(), r.language(),
                String.join(", ", r.hotwords() == null ? List.of() : r.hotwords()), r.mockTranscript()));
        Analysis analysis = analyze(transcript.text(), keyPoints(r.referenceAnswer()), r.language());
        var audio = r.wantFollowUpAudio() && analysis.followUpQuestion() != null
                ? tts.synthesize(analysis.followUpQuestion(), r.language()) : null;
        return new AnswerResult(transcript, analysis, audio, null);
    }

    static Analysis analyze(String answer, List<String> keyPoints, Language language) {
        String lower = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        if (lower.contains("không biết") || lower.contains("i don't know") || lower.contains("i do not know")) {
            return new Analysis("INSUFFICIENT", List.of(), keyPoints, false, false, false, true, 0, List.of(), null,
                    "Mock: student said they do not know.", MODEL);
        }
        if (wordCount(answer) < SHORT_ANSWER_WORDS) {
            String point = keyPoints.isEmpty() ? "ý chính của câu hỏi" : keyPoints.getFirst();
            String followUp = language == Language.EN ? "Could you explain more about: " + point + "?"
                    : "Em có thể giải thích rõ hơn về ý: " + point + "?";
            return new Analysis("PARTIAL", List.of(), keyPoints, false, false, false, false, 0, List.of(), followUp,
                    "Mock: answer shorter than " + SHORT_ANSWER_WORDS + " words.", MODEL);
        }
        return new Analysis("COMPLETE", keyPoints, List.of(), false, false, false, false, 0, List.of(), null,
                "Mock: answer long enough.", MODEL);
    }

    /** Reference answers are bullet lists: one key point per "- " line. */
    static List<String> keyPoints(String referenceAnswer) {
        if (referenceAnswer == null) {
            return List.of();
        }
        return referenceAnswer.lines().map(String::trim).filter(l -> l.startsWith("-"))
                .map(l -> l.substring(1).trim()).filter(l -> !l.isEmpty()).toList();
    }

    static int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
