package swd392.group6.AIVES.exam.dto;


/** Each field is a JSON array or one pasted string ("SE190001, SE190002\nSE190003"). */
public record AddStudentsRequest(Object studentCodes, Object usernames) {
}
