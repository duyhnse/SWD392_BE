package swd392.group6.AIVES.grading.dto;


/** Optional optimistic-lock check for the POST actions. */
public record VersionRequest(Integer version) {
}
