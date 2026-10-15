package swd392.group6.AIVES.exam;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** Imported only by the exam integration tests: a movable clock and the test-data helper. */
@TestConfiguration(proxyBeanMethods = false)
@Import(ExamTestData.class)
class ExamTestConfiguration {

    static final Instant T0 = Instant.parse("2031-03-03T08:00:00Z");

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock(T0);
    }
}
