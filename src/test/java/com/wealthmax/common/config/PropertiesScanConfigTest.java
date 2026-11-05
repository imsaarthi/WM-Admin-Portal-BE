package com.wealthmax.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PropertiesScanConfigTest {

    @Test
    @DisplayName("the scan finds AppProperties and JwtProperties without naming them")
    void scanRegistersTheTypedProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesScanConfig.class)
                .withPropertyValues(
                        "app.name=wm-admin-portal-be",
                        "app.frontend.base-url=https://portal.example.test",
                        "app.frontend.reset-password-path=/reset-password",
                        "app.security.token-pepper=synthetic-pepper",
                        "jwt.secret=synthetic-secret-with-at-least-thirty-two-characters",
                        "jwt.issuer=WM-Admin-Portal-BE",
                        "jwt.access-token-expiry=3600000",
                        "jwt.refresh-token-expiry=604800000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AppProperties.class);
                    assertThat(context).hasSingleBean(JwtProperties.class);
                });
    }

    @Test
    @DisplayName("the application class does not carry the scan, so test slices do not bind app.* and jwt.*")
    void applicationClassHasNoPropertiesScan() {
        assertThat(com.wealthmax.WmAdminPortalApplication.class
                .isAnnotationPresent(org.springframework.boot.context.properties.ConfigurationPropertiesScan.class))
                .isFalse();
    }
}
