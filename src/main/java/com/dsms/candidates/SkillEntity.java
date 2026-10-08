package com.dsms.candidates;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "skill")
public class SkillEntity extends AuditedEntity {
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String normalizedName;
    @Column(nullable = false)
    private String status;
}
