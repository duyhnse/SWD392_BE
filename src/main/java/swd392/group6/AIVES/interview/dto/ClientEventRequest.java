package swd392.group6.AIVES.interview.dto;

import java.util.Map;

/** Something the browser saw that belongs in the audit trail (silence, tab hidden, camera lost …). */
public record ClientEventRequest(String type, Map<String, Object> details) {
}
