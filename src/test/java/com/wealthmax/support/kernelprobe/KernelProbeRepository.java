package com.wealthmax.support.kernelprobe;

import org.springframework.data.jpa.repository.JpaRepository;

/** Exists only for KernelPersistenceDatabaseTest. */
public interface KernelProbeRepository extends JpaRepository<KernelProbeEntity, Long> {
}
