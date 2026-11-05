package com.wealthmax.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockConfigTest {

    @Test
    @DisplayName("the Clock bean is the system clock in UTC, so no code depends on the server's time zone")
    void clockBeanIsSystemUtc() {
        new ApplicationContextRunner().withUserConfiguration(ClockConfig.class).run(context -> {
            Clock clock = context.getBean(Clock.class);

            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(Duration.between(clock.instant(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(2));
        });
    }
}
