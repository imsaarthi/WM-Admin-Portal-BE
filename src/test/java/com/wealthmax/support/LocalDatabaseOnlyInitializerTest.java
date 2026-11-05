package com.wealthmax.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * No database and no Spring Boot start-up: only the rule that decides whether a datasource URL is local.
 */
class LocalDatabaseOnlyInitializerTest {

    private static final String REFUSAL = "only run against a PostgreSQL database on this machine";

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/wm_admin_portal_test",
            "jdbc:postgresql://localhost/wm_admin_portal_test",
            "jdbc:postgresql://LOCALHOST:5432/wm_admin_portal_test",
            "jdbc:postgresql://127.0.0.1:5432/wm_admin_portal_test",
            "jdbc:postgresql://[::1]:5432/wm_admin_portal_test",
            "jdbc:postgresql://localhost:5432,127.0.0.1:5433/wm_admin_portal_test",
            "jdbc:postgresql://localhost:5432/wm_admin_portal_test?ssl=false&connectTimeout=5"
    })
    void aPostgresUrlOnThisMachineIsAccepted(String url) {
        assertThatCode(() -> initialize(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://db.example.com:5432/wm_admin_portal_test",
            "jdbc:postgresql://10.0.0.5:5432/wm_admin_portal_test",
            "jdbc:postgresql://localhost.example.com:5432/wm_admin_portal_test",
            "jdbc:postgresql://mylocalhost:5432/wm_admin_portal_test",
            "jdbc:postgresql://localhost:5432,db.example.com:5432/wm_admin_portal_test",
            "jdbc:postgresql://localhost@db.example.com:5432/wm_admin_portal_test",
            "jdbc:postgresql://:5432/wm_admin_portal_test",
            "jdbc:postgresql:///wm_admin_portal_test",
            "jdbc:postgresql:wm_admin_portal_test",
            "jdbc:h2:mem:test",
            "jdbc:mysql://localhost:3306/wm_admin_portal_test",
            "   ",
            ""
    })
    void anythingElseIsRefused(String url) {
        assertThatThrownBy(() -> initialize(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REFUSAL);
    }

    @Test
    void aMissingUrlIsRefused() {
        GenericApplicationContext context = new GenericApplicationContext();

        assertThatThrownBy(() -> new LocalDatabaseOnlyInitializer().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REFUSAL);
    }

    @Test
    void theMessageNamesTheHostButNeverTheWholeUrlSoAPasswordInTheUrlIsNotLeaked() {
        assertThatThrownBy(() -> initialize("jdbc:postgresql://db.example.com:5432/x?user=app&password=s3cret"))
                .hasMessageContaining("db.example.com")
                .hasMessageNotContaining("s3cret")
                .hasMessageNotContaining("password");
    }

    private static void initialize(String url) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("spring.datasource.url", url)));
        new LocalDatabaseOnlyInitializer().initialize(context);
    }
}
