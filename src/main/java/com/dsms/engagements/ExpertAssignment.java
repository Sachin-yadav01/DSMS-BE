package com.dsms.engagements;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "expert_assignment")
public class ExpertAssignment extends AuditedEntity {
    @Column(nullable = false)
    private Long engagementId;
    @Column(nullable = false)
    private Long expertId;
    @Column(nullable = false)
    private Instant effectiveStart;
    private Instant effectiveEnd;
    @Column(nullable = false)
    private String acceptanceState;
    @Column(nullable = false)
    private BigDecimal hourlyRateSnapshot;
    @Column(nullable = false)
    private String currency;
    private String replacementReason;
}
