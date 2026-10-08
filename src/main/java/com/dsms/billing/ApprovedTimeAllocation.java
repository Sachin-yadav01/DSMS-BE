package com.dsms.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "approved_time_allocation")
public class ApprovedTimeAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long workEntryId;
    @Column(nullable = false)
    private Long revisionId;
    @Column(nullable = false)
    private Long billingPeriodId;
    @Column(nullable = false)
    private Long assignmentId;
    @Column(nullable = false)
    private Long expertId;
    @Column(nullable = false)
    private Long candidateId;
    @Column(nullable = false)
    private int approvedActualSeconds;
    @Column(nullable = false)
    private int billableSeconds;
    @Column(nullable = false)
    private int payableSeconds;
    @Column(nullable = false)
    private Instant createdOn;
}
