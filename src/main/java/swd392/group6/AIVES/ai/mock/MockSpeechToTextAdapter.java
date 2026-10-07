package swd392.group6.AIVES.ai.mock;

import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.ai.Transcript;
import swd392.group6.AIVES.common.Language;

/** Returns the dev-supplied text (X-Mock-Transcript) or a fixed fixture answer. */
public class MockSpeechToTextAdapter implements SpeechToTextPort {

    static final String MODEL = "mock-stt";
    static final String FIXTURE_VI = "Dạ theo em thì kiến trúc này chia hệ thống thành các module có ranh giới rõ ràng, "
            + "mỗi module sở hữu dữ liệu riêng và giao tiếp với nhau qua interface, nhờ vậy dễ bảo trì và dễ kiểm thử hơn.";
    static final String FIXTURE_EN = "I think this architecture splits the system into modules with clear boundaries, "
            + "each module owns its data and they talk through interfaces, so it is easier to maintain and to test.";

    @Override
    public Transcript transcribe(SpeechToTextRequest request) {
        if (request.mockTranscript() != null && !request.mockTranscript().isBlank()) {
            return new Transcript(request.mockTranscript().trim(), MODEL, 0);
        }
        return new Transcript(request.language() == Language.EN ? FIXTURE_EN : FIXTURE_VI, MODEL, 0);
    }
}
