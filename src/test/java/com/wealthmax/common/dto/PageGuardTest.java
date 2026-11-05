package com.wealthmax.common.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.wealthmax.common.exception.InvalidDataException;

class PageGuardTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "title");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    @DisplayName("no sort given: the page uses the default sort")
    void noSort_usesDefaultSort() {
        Pageable pageable = PageGuard.pageable(2, 25, null, null, ALLOWED, DEFAULT_SORT);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT_SORT);
    }

    @Test
    @DisplayName("an allowed sort field is used, ascending unless told otherwise")
    void allowedSortField_isUsed() {
        assertThat(PageGuard.pageable(0, 10, "title", null, ALLOWED, DEFAULT_SORT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "title"));
        assertThat(PageGuard.pageable(0, 10, "title", "DESC", ALLOWED, DEFAULT_SORT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "title"));
    }

    @Test
    @DisplayName("a size of exactly 100 is accepted")
    void sizeAtTheCap_isAccepted() {
        assertThat(PageGuard.pageable(0, 100, null, null, ALLOWED, DEFAULT_SORT).getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("a negative page is rejected")
    void negativePage_isRejected() {
        assertThatThrownBy(() -> PageGuard.pageable(-1, 10, null, null, ALLOWED, DEFAULT_SORT))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("page must be 0 or more");
    }

    @Test
    @DisplayName("a size of 0 or above 100 is rejected, not silently clamped")
    void sizeOutOfRange_isRejected() {
        assertThatThrownBy(() -> PageGuard.pageable(0, 0, null, null, ALLOWED, DEFAULT_SORT))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("size must be between 1 and 100");
        assertThatThrownBy(() -> PageGuard.pageable(0, 101, null, null, ALLOWED, DEFAULT_SORT))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("size must be between 1 and 100");
    }

    @Test
    @DisplayName("a sort field outside the allow-list is rejected and not repeated back")
    void unknownSortField_isRejected() {
        assertThatThrownBy(() -> PageGuard.pageable(0, 10, "passwordHash", null, ALLOWED, DEFAULT_SORT))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Unsupported sort field")
                .hasMessageNotContaining("passwordHash");
    }

    @Test
    @DisplayName("a sort direction other than asc or desc is rejected")
    void unknownDirection_isRejected() {
        assertThatThrownBy(() -> PageGuard.pageable(0, 10, "title", "sideways", ALLOWED, DEFAULT_SORT))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("direction must be asc or desc");
    }
}
