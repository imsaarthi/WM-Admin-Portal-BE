package com.wealthmax.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Database tests run on the developer's own local database. Some assert exact row counts or the first generated
 * number, so they need their tables to be empty when they start.
 *
 * This check only looks: it never deletes anything. If a table holds rows (for example from trying the API in
 * Swagger) the tests stop with a message instead of failing in a confusing way, and no data is lost. The tests
 * roll back everything they write, so a passing run leaves the tables as it found them.
 *
 * Tables are named with @TablesMustBeEmpty on the test class.
 */
public class TablesMustBeEmptyExtension implements BeforeAllCallback {

    private static final Pattern PLAIN_TABLE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    @Override
    public void beforeAll(ExtensionContext context) {
        TablesMustBeEmpty tables = AnnotationSupport
                .findAnnotation(context.getRequiredTestClass(), TablesMustBeEmpty.class)
                .orElse(null);
        if (tables == null) {
            return;
        }
        JdbcTemplate jdbcTemplate = SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
        verifyEmpty(jdbcTemplate, tables.value());
    }

    static void verifyEmpty(JdbcTemplate jdbcTemplate, String[] tables) {
        // The names come from code, not from a user, but a name is joined into SQL, so only plain names get through.
        for (String table : tables) {
            if (!PLAIN_TABLE_NAME.matcher(table).matches()) {
                throw new IllegalArgumentException("Not a plain table name: " + table);
            }
        }

        List<String> notEmpty = new ArrayList<>();
        for (String table : tables) {
            Long rows = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
            if (rows != null && rows > 0) {
                notEmpty.add(table + " has " + rows + " row(s)");
            }
        }
        if (!notEmpty.isEmpty()) {
            throw new IllegalStateException("These database tests need empty tables, but "
                    + String.join(" and ", notEmpty) + ". The tests never delete data: remove those rows "
                    + "(and the rows that depend on them) yourself, then run the tests again.");
        }
    }
}
