package com.dsms.timetracking;

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
@Table(name = "work_entry_revision")
public class WorkEntryRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long workEntryId;
    @Column(nullable = false)
    private int revisionNo;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private int proposedSeconds;
    private Integer approvedActualSeconds;
    private Integer billableSeconds;
    private Integer payableSeconds;
    private String sharedSummary;
    private String correctionReason;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private Instant createdOn;
    private Instant supersededOn;
}
