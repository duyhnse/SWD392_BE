package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RetakeRequest(@Size(max = 200) String title, @NotNull Instant checkinOpensAt,
                     @NotNull Instant checkinClosesAt, Object studentCodes, Object usernames) {
}
