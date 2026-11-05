package com.wealthmax.support.kernelprobe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wealthmax.common.entity.AuditableEntity;

/**
 * Exists only for KernelPersistenceDatabaseTest. It is the smallest entity that uses the kernel base classes, so the
 * kernel can be proven on a real database before the first real module exists.
 */
@Entity
@Table(name = "kernel_probe")
public class KernelProbeEntity extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Needed by JPA. */
    protected KernelProbeEntity() {
    }

    public KernelProbeEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        this.name = newName;
    }
}
