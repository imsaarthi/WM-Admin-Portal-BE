package com.wealthmax.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The app.* block of application.yml. A missing or blank value stops startup, so a wrong deployment
 * fails at boot and not on the first request that needs it.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String name,
        String version,
        @Valid @NotNull Frontend frontend,
        @Valid @NotNull Security security) {

    /** Where the admin front end lives; used to build links in emails. */
    public record Frontend(@NotBlank String baseUrl, @NotBlank String resetPasswordPath) {
    }

    /** tokenPepper is mixed into refresh and password-reset token hashes. Its value belongs to DevOps. */
    public record Security(@NotBlank String tokenPepper) {
    }
}
