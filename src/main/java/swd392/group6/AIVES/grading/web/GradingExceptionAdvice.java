package swd392.group6.AIVES.grading.web;

import swd392.group6.AIVES.grading.service.GradesIncompleteException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import swd392.group6.AIVES.common.GlobalExceptionHandler;

/** Adds the list of incomplete threads to the GRADES_INCOMPLETE problem (06 §4 "list threads"). */
@RestControllerAdvice(basePackageClasses = GradingExceptionAdvice.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GradingExceptionAdvice {

    @ExceptionHandler(GradesIncompleteException.class)
    ProblemDetail gradesIncomplete(GradesIncompleteException ex) {
        ProblemDetail body = GlobalExceptionHandler.problem(ex.getStatus(), ex.getCode(), ex.getMessage());
        body.setProperty("threads", ex.getThreads());
        return body;
    }
}
