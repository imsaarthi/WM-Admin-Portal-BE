package com.wealthmax.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Code asks for the time through this Clock, never through Instant.now() or LocalDateTime.now(), so tests
 * can fix the time and nothing depends on the server's time zone (finding OTH-11).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
