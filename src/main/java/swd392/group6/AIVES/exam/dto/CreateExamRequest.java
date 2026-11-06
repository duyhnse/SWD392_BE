package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

// ---- buổi thi -------------------------------------------------------------------------------------------/** Missing connection rules take the {@code viva_exams} defaults; the examiner defaults to the caller. */
public record CreateExamRequest(@NotNull UUID templateId, @NotBlank @Size(max = 200) String title, String description,
                         String instructions, @Size(max = 150) String location, UUID examinerId,
                         @NotNull Instant checkinOpensAt, @NotNull Instant checkinClosesAt,
                         Integer reconnectGraceSec, Integer maxDisconnects, Integer maxFrozenSec,
                         Integer replaceMainAfterSec) {
}
