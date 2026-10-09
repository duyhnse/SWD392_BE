package swd392.group6.AIVES.ai.mock;

import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.ai.InterviewAiPort.AnswerRequest;
import swd392.group6.AIVES.ai.InterviewAiPort.AnswerResult;
import swd392.group6.AIVES.common.Language;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mock answer analysis of 02 §5, used by the WF3 runtime until the AI node is ready. */
class MockInterviewAiTest {

    private final MockInterviewAi ai = new MockInterviewAi(new MockSpeechToTextAdapter(), new MockTextToSpeechAdapter());

    private AnswerResult answer(String transcript, Language language, boolean wantAudio) {
        return ai.processAnswer(new AnswerRequest("turn-1", language, new byte[] {1}, "audio/webm", List.of("SOLID"),
                "Main?", "- Một deployable\n- Ranh giới module", List.of(), "Main?", 0, 2, wantAudio, transcript));
    }

    @Test
    void shortAnswerGetsAFollowUpAboutTheFirstMissingKeyPoint() {
        AnswerResult r = answer("Là một khối", Language.VI, true);
        assertThat(r.transcript().text()).isEqualTo("Là một khối");
        assertThat(r.analysis().coverage()).isEqualTo("PARTIAL");
        assertThat(r.analysis().followUpQuestion()).isEqualTo("Em có thể giải thích rõ hơn về ý: Một deployable?");
        assertThat(r.analysis().missingPoints()).containsExactly("Một deployable", "Ranh giới module");
        assertThat(r.followUpAudio()).isNotNull();
        assertThat(r.analysisError()).isNull();
    }

    @Test
    void dontKnowIsOffTopicWithoutFollowUp() {
        AnswerResult r = answer("Dạ em không biết ạ", Language.VI, true);
        assertThat(r.analysis().offTopicOrWrong()).isTrue();
        assertThat(r.analysis().followUpQuestion()).isNull();
        assertThat(r.followUpAudio()).isNull();
    }

    @Test
    void longAnswerIsCompleteAndEnglishFollowUpsAreEnglish() {
        AnswerResult complete = answer("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen",
                Language.EN, false);
        assertThat(complete.analysis().coverage()).isEqualTo("COMPLETE");
        AnswerResult partial = answer("short", Language.EN, false);
        assertThat(partial.analysis().followUpQuestion()).startsWith("Could you explain more about");
        assertThat(partial.followUpAudio()).isNull();
    }
}
