package swd392.group6.AIVES.user.dto;

import java.util.List;

public record ImportReport(int total, int created, int existing, List<RowError> errors) {
}
