package swd392.group6.AIVES.exam.dto;

import swd392.group6.AIVES.exam.service.QuestionSelector;

import java.util.List;

/** Pool check: rows the pool cannot serve (publish and check-in refuse with POOL_TOO_SMALL). */
public record PoolCheck(boolean sufficient, List<QuestionSelector.RowShortage> shortages) {
}
