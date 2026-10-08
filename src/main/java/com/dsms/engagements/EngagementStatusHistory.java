package com.dsms.engagements;

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
@Table(name = "engagement_status_history")
public class EngagementStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long engagementId;
    private String fromStatus;
    @Column(nullable = false)
    private String toStatus;
    private String reason;
    private Long actorUserId;
    @Column(nullable = false)
    private Instant createdOn;
}
