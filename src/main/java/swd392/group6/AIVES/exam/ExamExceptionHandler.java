package swd392.group6.AIVES.exam;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import swd392.group6.AIVES.common.GlobalExceptionHandler;

/** Adds the per-row details to POOL_TOO_SMALL; everything else falls through to {@link GlobalExceptionHandler}. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = VivaExamController.class)
class ExamExceptionHandler {

    @ExceptionHandler(PoolTooSmallException.class)
    ProblemDetail poolTooSmall(PoolTooSmallException ex) {
        ProblemDetail body = GlobalExceptionHandler.problem(ex.getStatus(), ex.getCode(), ex.getMessage());
        body.setProperty("rows", ex.getRows());
        return body;
    }

    /** Two lecturers saved the same buổi thi at once (optimistic locking on {@code viva_exams.version}). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail versionConflict() {
        return GlobalExceptionHandler.problem(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "The exam was changed by someone else; reload it");
    }
}
