package com.dsms.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "outbox_job")
public class OutboxJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payloadJson;

    @Column(nullable = false)
    private String dedupKey;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptOn;

    private String lastError;

    @Column(nullable = false)
    private Instant createdOn;

    private Instant processedOn;
}
