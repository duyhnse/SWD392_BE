package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the interview sweeper every 5 s: time up, offline detection, stuck processing, unanswered questions
 * (05 §6, §8). Off in the {@code test} profile; tests call {@link InterviewService#sweep()} with a fixed clock.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
class InterviewSweeper {

    private final InterviewService service;

    @Scheduled(fixedDelay = 5_000, initialDelay = 15_000)
    void tick() {
        service.sweep();
    }
}
