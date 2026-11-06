package com.wealthmax.common.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

/**
 * Finds every @ConfigurationProperties record under com.wealthmax.
 *
 * Kept off the application class on purpose: test slices (@WebMvcTest, @DataJpaTest) load the application
 * class, so a scan there makes every slice bind app.* and jwt.* and fail without a full config.
 * Slices skip @Configuration classes, so they skip this one.
 */
@Configuration
@ConfigurationPropertiesScan("com.wealthmax")
public class PropertiesScanConfig {
}
