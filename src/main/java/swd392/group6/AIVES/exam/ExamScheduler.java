package swd392.group6.AIVES.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opens and closes buổi thi on time every minute (BR-E4). Disabled in the {@code test} profile so integration
 * tests of other modules keep the exam rows they insert; tests call {@link #tick()} explicitly.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
class ExamScheduler {

    private final ExamStatusRefresher refresher;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    void tick() {
        refresher.refreshDue();
    }
}
