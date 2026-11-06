package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** {@code null} = unchanged; an empty string clears description / instructions / location. */
public record UpdateExamRequest(@NotNull Integer version, UUID templateId, @Size(max = 200) String title,
                         String description, String instructions, @Size(max = 150) String location,
                         UUID examinerId, Instant checkinOpensAt, Instant checkinClosesAt,
                         Integer reconnectGraceSec, Integer maxDisconnects, Integer maxFrozenSec,
                         Integer replaceMainAfterSec) {

    /** Fields that cannot change once students may check in. */
    public boolean touchesFrozenFields() {
        return templateId != null || checkinOpensAt != null || reconnectGraceSec != null
                || maxDisconnects != null || maxFrozenSec != null || replaceMainAfterSec != null;
    }
}
