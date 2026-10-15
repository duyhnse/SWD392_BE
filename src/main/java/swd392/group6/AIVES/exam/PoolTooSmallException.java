package swd392.group6.AIVES.exam;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import swd392.group6.AIVES.common.ApiException;

import java.util.List;

/** 422 POOL_TOO_SMALL with one entry per blueprint row that cannot be satisfied (15 §3). */
@Getter
class PoolTooSmallException extends ApiException {

    private final transient List<QuestionSelector.RowShortage> rows;

    PoolTooSmallException(List<QuestionSelector.RowShortage> rows) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, QuestionSelector.POOL_TOO_SMALL, describe(rows));
        this.rows = rows;
    }

    private static String describe(List<QuestionSelector.RowShortage> rows) {
        QuestionSelector.RowShortage first = rows.getFirst();
        return "Not enough PUBLISHED questions: need " + first.required() + ", have " + first.available()
                + (rows.size() > 1 ? " (and " + (rows.size() - 1) + " more row(s))" : "");
    }
}
