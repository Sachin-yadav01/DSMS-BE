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
@Table(name = "work_segment")
public class WorkSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long workEntryId;
    @Column(nullable = false)
    private Instant startedOn;
    private Instant endedOn;
    private String pauseReason;
    @Column(insertable = false, updatable = false)
    private Long openEntryId;
    @Column(nullable = false)
    private Instant createdOn;
}
