package swd392.group6.AIVES.exam;

/** Visibility of a lượt thi's result for the student (15 §2.2, D32). */
enum ResultStatus {
    NONE,
    PENDING,
    RELEASED;

    static ResultStatus of(String sessionStatus, String evaluationStatus, boolean resultsReleased) {
        if (!"COMPLETED".equals(sessionStatus)) {
            return NONE;
        }
        return "CONFIRMED".equals(evaluationStatus) && resultsReleased ? RELEASED : PENDING;
    }
}
