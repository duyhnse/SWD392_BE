package swd392.group6.AIVES.interview.dto;

import java.math.BigDecimal;

/**
 * {@code threadIndex} 1-based; {@code threadRemainingSec} = answering time left for this main question (D46);
 * {@code maxPoints} = points of the current question out of 10 (D81), null = equal weight.
 */
public record Progress(int threadIndex, int threadCount, int followupIndex, int maxFollowups, int threadRemainingSec,
                       BigDecimal maxPoints) {
}
