package com.dsms.candidates;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "expert_profile")
public class ExpertProfile extends AuditedEntity {
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String displayCode;
    @Column(nullable = false)
    private String fullName;
    private String phone;
    private String email;
    private Integer experienceYears;
    private String profileSummary;
    private String notes;
    @Column(nullable = false)
    private BigDecimal hourlyPayoutRate;
    @Column(nullable = false)
    private String currency;
    private String paymentType;
    @Column(nullable = false)
    private String workingTime;
    @Column(nullable = false)
    private String status;
}
