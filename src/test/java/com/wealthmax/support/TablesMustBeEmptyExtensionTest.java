package com.wealthmax.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * No database: the guard's logic with a mocked JdbcTemplate. The guard is used for real by the database tests.
 */
@ExtendWith(MockitoExtension.class)
class TablesMustBeEmptyExtensionTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("empty tables let the run continue")
    void emptyTablesPass() {
        when(jdbc.queryForObject("select count(*) from kernel_probe", Long.class)).thenReturn(0L);

        assertThatCode(() -> TablesMustBeEmptyExtension.verifyEmpty(jdbc, new String[] {"kernel_probe"}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a table that holds rows stops the run and says how many")
    void tableWithRowsStopsTheRun() {
        when(jdbc.queryForObject("select count(*) from kernel_probe", Long.class)).thenReturn(3L);

        assertThatThrownBy(() -> TablesMustBeEmptyExtension.verifyEmpty(jdbc, new String[] {"kernel_probe"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kernel_probe has 3 row(s)")
                .hasMessageContaining("never delete");
    }

    @Test
    @DisplayName("every non-empty table is named, not only the first")
    void everyNonEmptyTableIsNamed() {
        when(jdbc.queryForObject("select count(*) from first_table", Long.class)).thenReturn(1L);
        when(jdbc.queryForObject("select count(*) from second_table", Long.class)).thenReturn(2L);

        assertThatThrownBy(() -> TablesMustBeEmptyExtension.verifyEmpty(jdbc,
                new String[] {"first_table", "second_table"}))
                .hasMessageContaining("first_table has 1 row(s)")
                .hasMessageContaining("second_table has 2 row(s)");
    }

    @Test
    @DisplayName("it only counts: no delete, update or DDL is ever issued")
    void neverDeletesAnything() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);

        assertThatThrownBy(() -> TablesMustBeEmptyExtension.verifyEmpty(jdbc, new String[] {"kernel_probe"}))
                .isInstanceOf(IllegalStateException.class);

        verify(jdbc, never()).update(anyString());
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    @DisplayName("a table name that is not a plain identifier is refused before any SQL runs")
    void unsafeTableNameIsRefused() {
        assertThatThrownBy(() -> TablesMustBeEmptyExtension.verifyEmpty(jdbc,
                new String[] {"kernel_probe; drop table users"}))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbc);
    }
}
