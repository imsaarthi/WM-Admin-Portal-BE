package com.wealthmax.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * No database and no full application: only the binding of app.* and jwt.* to the typed properties.
 * Values are synthetic.
 */
class AppPropertiesBindingTest {

    private static final String LONG_SECRET = "synthetic-secret-with-at-least-thirty-two-characters";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "app.name=wm-admin-portal-be",
                    "app.version=0.0.1",
                    "app.frontend.base-url=https://portal.example.test",
                    "app.frontend.reset-password-path=/reset-password",
                    "app.security.token-pepper=synthetic-pepper",
                    "jwt.secret=" + LONG_SECRET,
                    "jwt.issuer=WM-Admin-Portal-BE",
                    "jwt.access-token-expiry=3600000",
                    "jwt.refresh-token-expiry=604800000");

    @EnableConfigurationProperties({AppProperties.class, JwtProperties.class})
    static class PropertiesConfig {
    }

    @Test
    @DisplayName("every key in application.yml binds to a typed field")
    void bindsAppAndJwtProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            AppProperties app = context.getBean(AppProperties.class);
            assertThat(app.name()).isEqualTo("wm-admin-portal-be");
            assertThat(app.version()).isEqualTo("0.0.1");
            assertThat(app.frontend().baseUrl()).isEqualTo("https://portal.example.test");
            assertThat(app.frontend().resetPasswordPath()).isEqualTo("/reset-password");
            assertThat(app.security().tokenPepper()).isEqualTo("synthetic-pepper");

            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(jwt.secret()).isEqualTo(LONG_SECRET);
            assertThat(jwt.issuer()).isEqualTo("WM-Admin-Portal-BE");
        });
    }

    @Test
    @DisplayName("token expiries written as plain numbers are read as milliseconds")
    void expiriesArePlainMilliseconds() {
        runner.run(context -> {
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(jwt.accessTokenExpiry()).isEqualTo(Duration.ofHours(1));
            assertThat(jwt.refreshTokenExpiry()).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    @DisplayName("a blank jwt secret stops startup")
    void blankJwtSecretFailsStartup() {
        runner.withPropertyValues("jwt.secret=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("secret");
        });
    }

    @Test
    @DisplayName("a jwt secret shorter than 32 characters stops startup (HS256 needs 256 bits)")
    void shortJwtSecretFailsStartup() {
        runner.withPropertyValues("jwt.secret=too-short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("secret");
        });
    }

    @Test
    @DisplayName("a missing token pepper stops startup")
    void missingTokenPepperFailsStartup() {
        runner.withPropertyValues("app.security.token-pepper=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("tokenPepper");
        });
    }
}
