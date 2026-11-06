package swd392.group6.AIVES.interview.service;

import org.junit.jupiter.api.Test;
import swd392.group6.AIVES.ai.InterviewAiPort.Analysis;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The follow-up decision table of 05 §5 — pure, no AI (D03). */
class FollowupDeciderTest {

    private static Analysis analysis(String coverage, boolean vague, boolean wrong, String followUp) {
        return new Analysis(coverage, List.of(), List.of(), vague, false, false, wrong, 0, List.of(), followUp, "", "m");
    }

    @Test
    void limitAndTimeAreCheckedBeforeAnyAnalysis() {
        assertThat(FollowupDecider.forced(2, 2, 300, 600)).isEqualTo("NEXT_LIMIT");
        assertThat(FollowupDecider.forced(0, 2, 29, 600)).isEqualTo("NEXT_TIME");
        assertThat(FollowupDecider.forced(0, 2, 300, 20)).isEqualTo("NEXT_TIME");
        assertThat(FollowupDecider.forced(1, 2, 300, 600)).isNull();
        assertThat(FollowupDecider.forced(0, 0, 300, 600)).isEqualTo("NEXT_LIMIT");
    }

    @Test
    void decisionTable() {
        assertThat(FollowupDecider.decide(null)).isEqualTo("NEXT_AI_ERROR");
        assertThat(FollowupDecider.decide(analysis("PARTIAL", false, true, "Vì sao?"))).isEqualTo("NEXT_WRONG");
        assertThat(FollowupDecider.decide(analysis("PARTIAL", false, false, "Vì sao?"))).isEqualTo("FOLLOW_UP");
        assertThat(FollowupDecider.decide(analysis("COMPLETE", true, false, "Ý em là gì?"))).isEqualTo("FOLLOW_UP");
        assertThat(FollowupDecider.decide(analysis("COMPLETE", false, false, null))).isEqualTo("NEXT_COMPLETE");
        assertThat(FollowupDecider.decide(analysis("PARTIAL", false, false, " "))).isEqualTo("NEXT_COMPLETE");
        assertThat(FollowupDecider.decide(analysis("PARTIAL", false, false, "từ ".repeat(41)))).isEqualTo("NEXT_AI_ERROR");
    }

    @Test
    void noiseIsNoAnswer_EX5() {
        assertThat(FollowupDecider.isNoAnswer("ừm")).isTrue();
        assertThat(FollowupDecider.isNoAnswer("  ")).isTrue();
        assertThat(FollowupDecider.isNoAnswer("dạ em nghĩ")).isFalse();
    }
}
