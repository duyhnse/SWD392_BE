package swd392.group6.AIVES.questionbank.internal;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import swd392.group6.AIVES.common.ApiException;

import java.util.List;

/** 422 with the list of failed publish rules (BR-Q11 when saving a PUBLISHED question). */
@Getter
public class PublishValidationException extends ApiException {

    private final List<String> errors;

    public PublishValidationException(List<String> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "PUBLISH_VALIDATION_FAILED",
                "The question no longer satisfies the publish rules: " + String.join(", ", errors));
        this.errors = List.copyOf(errors);
    }
}
