package swd392.group6.AIVES.grading.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 03_DATA_MODEL.md §4 score formulas. */
class ScoreCalculatorTest {

    private static ScoreCalculator.Part part(String score, String max, String weight) {
        return new ScoreCalculator.Part(score == null ? null : new BigDecimal(score), new BigDecimal(max), new BigDecimal(weight));
    }

    private static List<BigDecimal> values(String... v) {
        return Arrays.stream(v).map(s -> s == null ? null : new BigDecimal(s)).toList();
    }

    @Test
    void weightedQuestionScore_AC_G2() {
        // w 60/40, max 10/10, scores 8/5 → 0.8×0.6×10 + 0.5×0.4×10 = 6.80
        assertThat(ScoreCalculator.questionScore(List.of(part("8", "10", "60"), part("5", "10", "40"))))
                .isEqualByComparingTo("6.80").hasScaleOf(2);
    }

    @Test
    void differentScalesAndHalfUpRounding() {
        // 3/4 × 50% + 1/3 × 50% → (0.375 + 0.16667) × 10 = 5.41666… → 5.42
        assertThat(ScoreCalculator.questionScore(List.of(part("3", "4", "50"), part("1", "3", "50"))))
                .isEqualByComparingTo("5.42");
        // 2.5/6 × 100% → 4.1666… → 4.17
        assertThat(ScoreCalculator.questionScore(List.of(part("2.5", "6", "100")))).isEqualByComparingTo("4.17");
        // 0.125 boundary: 1/8 × 100% ×10 = 1.25 exactly
        assertThat(ScoreCalculator.questionScore(List.of(part("1", "8", "100")))).isEqualByComparingTo("1.25");
        // full marks → 10.00
        assertThat(ScoreCalculator.questionScore(List.of(part("4", "4", "70"), part("5", "5", "30"))))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void incompleteThreadHasNoScore() {
        assertThat(ScoreCalculator.questionScore(List.of(part("8", "10", "60"), part(null, "10", "40")))).isNull();
        assertThat(ScoreCalculator.questionScore(List.of())).isNull();
    }

    @Test
    void meanAndMedianIgnoreNulls() {
        assertThat(ScoreCalculator.mean(values("6.80", "9.00", null))).isEqualByComparingTo("7.90");
        assertThat(ScoreCalculator.mean(values("1", "1", "2"))).isEqualByComparingTo("1.33");
        assertThat(ScoreCalculator.mean(values("1", "2", "2"))).isEqualByComparingTo("1.67");
        assertThat(ScoreCalculator.mean(values((String) null))).isNull();
        assertThat(ScoreCalculator.median(values("9", "1", "5"))).isEqualByComparingTo("5.00");
        assertThat(ScoreCalculator.median(values("8.4", "4.5"))).isEqualByComparingTo("6.45");
        assertThat(ScoreCalculator.median(values())).isNull();
    }

    @Test
    void finalScoresUseQuarterSteps() {
        BigDecimal max = new BigDecimal("10");
        assertThat(ScoreCalculator.isValidFinalScore(new BigDecimal("7.25"), max)).isTrue();
        assertThat(ScoreCalculator.isValidFinalScore(new BigDecimal("0"), max)).isTrue();
        assertThat(ScoreCalculator.isValidFinalScore(new BigDecimal("10.00"), max)).isTrue();
        assertThat(ScoreCalculator.isValidFinalScore(new BigDecimal("7.3"), max)).isFalse();
        assertThat(ScoreCalculator.isValidFinalScore(new BigDecimal("10.25"), max)).isFalse();
        assertThat(ScoreCalculator.isValidFinalScore(new BigDecimal("-0.25"), max)).isFalse();
        assertThat(ScoreCalculator.isValidFinalScore(null, max)).isFalse();
    }

    @Test
    void aiScoresRoundToNearestQuarter() {
        assertThat(ScoreCalculator.roundToQuarter(new BigDecimal("7.1"))).isEqualByComparingTo("7.00");
        assertThat(ScoreCalculator.roundToQuarter(new BigDecimal("7.13"))).isEqualByComparingTo("7.25");
        assertThat(ScoreCalculator.roundToQuarter(new BigDecimal("7.4"))).isEqualByComparingTo("7.50");
    }
}
