package swd392.group6.AIVES.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** List response shape from 09_API_SPEC.md §1: {@code {items, page, size, total}}. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
