package swd392.group6.AIVES.exam.dto;

import jakarta.validation.constraints.Size;

// ---- lượt thi (attempts) --------------------------------------------------------------------------------
public record CheckInRequest(boolean consentRecording, @Size(max = 300) String clientInfo) {
}
