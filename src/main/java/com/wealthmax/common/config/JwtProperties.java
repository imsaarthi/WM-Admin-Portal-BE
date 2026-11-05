package com.wealthmax.common.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The jwt.* block of application.yml. A plain number such as 3600000 is read as milliseconds.
 *
 * The secret must be at least 32 characters: HS256 needs a 256-bit key, and a shorter one is
 * rejected at startup instead of being accepted and weak. The secret's value belongs to DevOps.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenExpiry,
        @NotNull Duration refreshTokenExpiry) {
}
