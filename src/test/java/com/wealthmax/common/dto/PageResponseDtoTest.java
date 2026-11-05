package com.wealthmax.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseDtoTest {

    @Test
    @DisplayName("a middle page carries its numbers and is not last")
    void middlePage() {
        PageResponseDto<String> dto =
                PageResponseDto.from(new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));

        assertThat(dto.content()).containsExactly("a", "b");
        assertThat(dto.page()).isEqualTo(1);
        assertThat(dto.size()).isEqualTo(2);
        assertThat(dto.totalElements()).isEqualTo(5);
        assertThat(dto.totalPages()).isEqualTo(3);
        assertThat(dto.last()).isFalse();
    }

    @Test
    @DisplayName("the final page is marked last")
    void lastPage() {
        PageResponseDto<String> dto =
                PageResponseDto.from(new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5));

        assertThat(dto.last()).isTrue();
    }

    @Test
    @DisplayName("an empty result is one empty page, not an error")
    void emptyResult() {
        PageResponseDto<String> dto = PageResponseDto.from(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThat(dto.content()).isEmpty();
        assertThat(dto.totalElements()).isZero();
        assertThat(dto.last()).isTrue();
    }

    @Test
    @DisplayName("the content list cannot be changed after the DTO is built")
    void contentIsImmutable() {
        PageResponseDto<String> dto =
                PageResponseDto.from(new PageImpl<>(List.of("a"), PageRequest.of(0, 10), 1));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dto.content().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
