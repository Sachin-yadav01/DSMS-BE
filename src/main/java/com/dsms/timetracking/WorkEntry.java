package com.dsms.timetracking;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "work_entry")
public class WorkEntry extends AuditedEntity {
    @Column(nullable = false)
    private String displayCode;
    @Column(nullable = false)
    private Long engagementId;
    @Column(nullable = false)
    private Long assignmentId;
    @Column(nullable = false)
    private Long expertId;
    @Column(nullable = false)
    private Long candidateId;
    private Long sessionId;
    private Long requestId;
    @Column(nullable = false)
    private String source;
    @Column(nullable = false)
    private String category;
    private String timerState;
    @Column(nullable = false)
    private int recordedSeconds;
    private String sharedSummary;
    private String manualReason;
    @Column(nullable = false)
    private boolean reviewFlag;
    @Column(nullable = false)
    private boolean longSessionFlag;
    @Column(nullable = false)
    private boolean boundaryFlag;
    private Long effectiveRevisionId;
    @Column(nullable = false)
    private boolean forceStopped;
    private String forceStopReason;
    @Column(insertable = false, updatable = false)
    private Long openExpertId;
}
