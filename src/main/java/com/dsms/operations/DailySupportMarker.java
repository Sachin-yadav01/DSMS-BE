package com.dsms.operations;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "daily_support_marker")
public class DailySupportMarker extends AuditedEntity {
    @Column(nullable = false)
    private Long candidateId;
    @Column(nullable = false)
    private LocalDate supportDate;
    @Column(nullable = false)
    private String dayStatus;
    private String notes;
}
