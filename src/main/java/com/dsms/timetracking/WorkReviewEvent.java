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
@Table(name = "work_review_event")
public class WorkReviewEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long workEntryId;
    private Long revisionId;
    @Column(nullable = false)
    private String eventType;
    private String note;
    private Long actorUserId;
    @Column(nullable = false)
    private Instant createdOn;
}
