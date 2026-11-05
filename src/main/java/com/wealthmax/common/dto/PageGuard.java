package com.wealthmax.common.dto;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.wealthmax.common.exception.InvalidDataException;

/**
 * Turns the page, size and sort a client sent into a Pageable the database can safely use.
 *
 * - size is 1 to 100. A bigger value is rejected, not clamped, so a client never silently gets less than it asked for.
 * - A sort field must be on the endpoint's allow-list. Sorting by an arbitrary column name lets a client probe
 *   columns it should not see, and a wrong name would otherwise surface as a 500.
 * - Messages never repeat the rejected value back.
 */
public final class PageGuard {

    public static final int MAX_SIZE = 100;

    private PageGuard() {
    }

    public static Pageable pageable(int page, int size, String sortBy, String direction,
            Set<String> allowedSortFields, Sort defaultSort) {
        if (page < 0) {
            throw new InvalidDataException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidDataException("size must be between 1 and " + MAX_SIZE);
        }
        if (sortBy == null || sortBy.isBlank()) {
            return PageRequest.of(page, size, defaultSort);
        }
        if (!allowedSortFields.contains(sortBy)) {
            throw new InvalidDataException("Unsupported sort field");
        }
        return PageRequest.of(page, size, Sort.by(parseDirection(direction), sortBy));
    }

    private static Sort.Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Sort.Direction.ASC;
        }
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidDataException("direction must be asc or desc");
        };
    }
}
