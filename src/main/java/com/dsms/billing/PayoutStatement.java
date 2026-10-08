package com.dsms.billing;

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
@Table(name = "payout_statement")
public class PayoutStatement extends AuditedEntity {
    @Column(nullable = false)
    private String statementNumber;
    @Column(nullable = false)
    private Long expertId;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String currency;
    private LocalDate issueDate;
    @Column(nullable = false)
    private BigDecimal subtotal;
    @Column(nullable = false)
    private BigDecimal adjustmentTotal;
    @Column(nullable = false)
    private BigDecimal total;
    @Column(nullable = false)
    private BigDecimal paidAmount;
}
