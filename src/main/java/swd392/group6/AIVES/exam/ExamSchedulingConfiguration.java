package swd392.group6.AIVES.exam;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@link ExamScheduler}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ExamSchedulingConfiguration {
}
