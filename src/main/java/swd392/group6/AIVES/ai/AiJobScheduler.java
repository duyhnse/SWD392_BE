package swd392.group6.AIVES.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link AiJobService#sweep()} every 30 s; off in the {@code test} profile (tests call it directly). */
@Component
@Profile("!test")
@RequiredArgsConstructor
class AiJobScheduler {

    private final AiJobService jobs;

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    void tick() {
        jobs.sweep();
    }
}
