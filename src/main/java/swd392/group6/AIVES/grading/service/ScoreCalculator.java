package swd392.group6.AIVES.grading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Score formulas of 03_DATA_MODEL.md §4. All results are on the 0..10 scale, HALF_UP to 2 decimals. */
public final class ScoreCalculator {

    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal QUARTER_STEPS = BigDecimal.valueOf(4);
    private static final int WORK_SCALE = 10;

    private ScoreCalculator() {
    }

    /** One criterion of a thread: its score {@code s_i} (may be null), {@code max_i} and weight {@code w_i} in percent. */
    public record Part(BigDecimal score, BigDecimal maxScore, BigDecimal weightPercent) {
    }

    /**
     * {@code Σ (s_i / max_i) × (w_i / 100) × 10}, rounded HALF_UP to 2 decimals. Null when there are no parts or
     * any score is missing (the thread is not fully graded yet).
     */
    public static BigDecimal questionScore(List<Part> parts) {
        if (parts == null || parts.isEmpty() || parts.stream().anyMatch(p -> p.score() == null)) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Part p : parts) {
            // (s / max) × (w / 100) × 10  ==  s × w / max / 10
            sum = sum.add(p.score().multiply(p.weightPercent()).divide(p.maxScore(), WORK_SCALE, RoundingMode.HALF_UP));
        }
        return sum.divide(TEN, 2, RoundingMode.HALF_UP);
    }

    /** A question score (0..10) and its maximum points in the total (D81; null = equal weight). */
    public record Weighted(BigDecimal score, BigDecimal maxPoints) {
    }

    /**
     * Total on 10 of the included questions (D81): {@code Σ score_i × p_i / Σ p_i} when every question has points —
     * the sum of the points earned when all questions count, rescaled when some are left out — otherwise the mean of
     * the scores (equal weight). Questions without a score are skipped; null when none has one.
     */
    public static BigDecimal total(Collection<Weighted> questions) {
        List<Weighted> present = questions.stream().filter(q -> q.score() != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        if (present.stream().anyMatch(q -> q.maxPoints() == null || q.maxPoints().signum() <= 0)) {
            return mean(present.stream().map(Weighted::score).toList());
        }
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal points = BigDecimal.ZERO;
        for (Weighted q : present) {
            weighted = weighted.add(q.score().multiply(q.maxPoints()));
            points = points.add(q.maxPoints());
        }
        return weighted.divide(points, 2, RoundingMode.HALF_UP);
    }

    /** Points earned on one question: {@code score / 10 × maxPoints}, 2 decimals; null without score or points. */
    public static BigDecimal earned(BigDecimal score, BigDecimal maxPoints) {
        if (score == null || maxPoints == null) {
            return null;
        }
        return score.multiply(maxPoints).divide(TEN, 2, RoundingMode.HALF_UP);
    }

    /** Mean of the non-null values, 2 decimals HALF_UP; null when there is none. */
    public static BigDecimal mean(Collection<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    /** Median of the non-null values, 2 decimals HALF_UP; null when there is none. */
    public static BigDecimal median(Collection<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().filter(Objects::nonNull).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int n = sorted.size();
        BigDecimal median = n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), WORK_SCALE, RoundingMode.HALF_UP);
        return median.setScale(2, RoundingMode.HALF_UP);
    }

    /** A lecturer's final score must lie in [0, max] on a 0.25 grid (06 §4 step 7). */
    public static boolean isValidFinalScore(BigDecimal score, BigDecimal maxScore) {
        if (score == null || score.signum() < 0 || score.compareTo(maxScore) > 0) {
            return false;
        }
        return score.multiply(QUARTER_STEPS).stripTrailingZeros().scale() <= 0;
    }

    /** AI scores are rounded to the nearest 0.25 of the criterion scale before storage (03 §4). */
    public static BigDecimal roundToQuarter(BigDecimal score) {
        return score.multiply(QUARTER_STEPS).setScale(0, RoundingMode.HALF_UP)
                .divide(QUARTER_STEPS, 2, RoundingMode.HALF_UP);
    }
}
