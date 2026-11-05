package com.wealthmax.support.kernelprobe;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Limits a database slice to the probe's own entity and repository. This is the pattern every module follows: its
 * tests load only its own entities and repositories, so they cannot reach another module's tables, the same
 * rule the production code obeys.
 */
@TestConfiguration
@EntityScan(basePackageClasses = KernelProbeEntity.class)
@EnableJpaRepositories(basePackageClasses = KernelProbeRepository.class)
public class KernelProbeJpaTestConfig {
}
