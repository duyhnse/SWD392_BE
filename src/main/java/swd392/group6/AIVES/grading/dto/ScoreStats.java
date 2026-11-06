package swd392.group6.AIVES.grading.dto;

import java.math.BigDecimal;

// ----- report ---------------------------------------------------------------------------------------------------
public record ScoreStats(int count, BigDecimal mean, BigDecimal median, BigDecimal min, BigDecimal max) {
}
