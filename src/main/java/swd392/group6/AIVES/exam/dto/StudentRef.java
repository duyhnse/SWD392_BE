package swd392.group6.AIVES.exam.dto;

import java.util.UUID;

public record StudentRef(UUID studentId, String username, String studentCode, String fullName, int seqNo) {
}
