package com.dsms.scheduling;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "support_session")
public class SupportSession extends AuditedEntity {
    @Column(nullable = false)
    private String displayCode;
    @Column(nullable = false)
    private Long engagementId;
    @Column(nullable = false)
    private Long assignmentId;
    private Long seriesId;
    private String occurrenceKey;
    @Column(nullable = false)
    private Long candidateId;
    @Column(nullable = false)
    private Long expertId;
    @Column(nullable = false)
    private String candidateNameSnapshot;
    @Column(nullable = false)
    private String expertNameSnapshot;
    @Column(nullable = false)
    private Instant scheduledStart;
    @Column(nullable = false)
    private Instant scheduledEnd;
    @Column(nullable = false)
    private String sourceTimezone;
    private String meetingPlatform;
    private String meetingUrl;
    private String topic;
    @Column(nullable = false)
    private String status;
    private String cancellationReason;
    private Boolean candidateAttended;
    private Boolean expertAttended;
    private String sharedSummary;
}
