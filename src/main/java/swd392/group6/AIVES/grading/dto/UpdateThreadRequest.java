package swd392.group6.AIVES.grading.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code lecturerComment} / {@code includeInTotal} left null are unchanged; an empty comment clears it. */
public record UpdateThreadRequest(@NotNull Integer version, @Valid List<CriterionFinal> criteria,
                           @Size(max = 4000) String lecturerComment, Boolean includeInTotal) {
}
