package com.dsms.billing;

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
@Table(name = "billing_period")
public class BillingPeriod extends AuditedEntity {
    @Column(nullable = false)
    private Long engagementId;
    @Column(nullable = false)
    private Instant periodStart;
    @Column(nullable = false)
    private Instant periodEnd;
    @Column(nullable = false)
    private String businessTimezone;
    @Column(nullable = false)
    private Long termsId;
    @Column(nullable = false)
    private String planType;
    @Column(nullable = false)
    private String currency;
    private BigDecimal confirmedBaseFee;
    private Integer confirmedIncludedSeconds;
    @Column(nullable = false)
    private boolean overageEnabled;
    private BigDecimal overageHourlyRate;
    private BigDecimal hourlyRate;
    @Column(nullable = false)
    private boolean partialMonth;
    @Column(nullable = false)
    private boolean termsConfirmed;
    @Column(nullable = false)
    private String status;
}
