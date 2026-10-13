package swd392.group6.AIVES.interview.internal;

import org.springframework.boot.test.context.TestConfiguration;

/** Lets tests outside the package run the interview sweeper with their fixed clock (the scheduler is off in tests). */
@TestConfiguration(proxyBeanMethods = false)
public class InterviewTestHooks {

    private final InterviewService service;

    InterviewTestHooks(InterviewService service) {
        this.service = service;
    }

    public void sweep() {
        service.sweep();
    }
}
