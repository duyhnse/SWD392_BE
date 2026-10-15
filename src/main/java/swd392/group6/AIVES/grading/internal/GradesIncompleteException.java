package swd392.group6.AIVES.grading.internal;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import swd392.group6.AIVES.common.ApiException;

import java.util.List;

/** 409 GRADES_INCOMPLETE with the threads that still lack final scores (BR-G4). */
@Getter
class GradesIncompleteException extends ApiException {

    private final transient List<GradingDtos.IncompleteThread> threads;

    GradesIncompleteException(String message, List<GradingDtos.IncompleteThread> threads) {
        super(HttpStatus.CONFLICT, "GRADES_INCOMPLETE", message);
        this.threads = threads;
    }
}
