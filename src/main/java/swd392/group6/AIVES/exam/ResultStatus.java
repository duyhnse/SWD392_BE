package swd392.group6.AIVES.exam;

/** Visibility of an attempt's result for the student (15 §2.2, D32). */
enum ResultStatus {
    NONE,
    PENDING,
    RELEASED;

    static ResultStatus of(String attemptStatus, String evaluationStatus, boolean resultsReleased) {
        if (!"COMPLETED".equals(attemptStatus)) {
            return NONE;
        }
        return "CONFIRMED".equals(evaluationStatus) && resultsReleased ? RELEASED : PENDING;
    }
}
