package com.dsms.engagements;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "engagement_terms")
public class EngagementTerms extends AuditedEntity {
    @Column(nullable = false)
    private Long engagementId;
    @Column(nullable = false)
    private String planType;
    @Column(nullable = false)
    private String currency;
    private BigDecimal hourlyRate;
    private BigDecimal monthlyBaseFee;
    private Integer includedSeconds;
    @Column(nullable = false)
    private boolean overageEnabled;
    private BigDecimal overageHourlyRate;
    private String evidenceNote;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private LocalDate effectiveFrom;
}
