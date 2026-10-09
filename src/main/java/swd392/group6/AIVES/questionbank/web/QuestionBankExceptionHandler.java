package swd392.group6.AIVES.questionbank.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import swd392.group6.AIVES.common.GlobalExceptionHandler;
import swd392.group6.AIVES.questionbank.internal.PublishValidationException;

import java.util.List;
import java.util.Map;

/** Adds the failed publish rules to the problem body: {@code errors: [{code}]} (BR-Q11). */
@RestControllerAdvice(assignableTypes = QuestionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class QuestionBankExceptionHandler {

    @ExceptionHandler(PublishValidationException.class)
    public ProblemDetail handlePublishValidation(PublishValidationException ex) {
        ProblemDetail body = GlobalExceptionHandler.problem(ex.getStatus(), ex.getCode(), ex.getMessage());
        List<Map<String, String>> errors = ex.getErrors().stream().map(code -> Map.of("code", code)).toList();
        body.setProperty("errors", errors);
        return body;
    }
}
