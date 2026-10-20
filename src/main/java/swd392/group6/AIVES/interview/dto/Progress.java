package swd392.group6.AIVES.interview.dto;


/** {@code threadIndex} 1-based; {@code threadRemainingSec} = answering time left for this main question (D46). */
public record Progress(int threadIndex, int threadCount, int followupIndex, int maxFollowups, int threadRemainingSec) {
}
