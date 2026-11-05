package com.wealthmax.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * The shape of every list response. Page numbers start at 0. Built only from a Spring Data Page,
 * so no endpoint hand-assembles one and the field names stay the same everywhere.
 */
public record PageResponseDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    /** The content list is copied, so a caller cannot change a response after it was built. */
    public PageResponseDto {
        content = List.copyOf(content);
    }

    public static <T> PageResponseDto<T> from(Page<T> page) {
        return new PageResponseDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
