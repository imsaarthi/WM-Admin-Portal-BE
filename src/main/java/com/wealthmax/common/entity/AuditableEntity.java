package com.wealthmax.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import lombok.Getter;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * For tables that record who changed a row. The columns hold the user's id (BIGINT), not a name or an
 * email, so no personal data is copied into every row.
 *
 * They stay null until the identity module supplies the signed-in user (Phase 3).
 */
@MappedSuperclass
@Getter
public abstract class AuditableEntity extends BaseEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
}
