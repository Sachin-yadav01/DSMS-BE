package com.dsms.candidates;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "expert_availability_rule")
public class ExpertAvailabilityRule extends AuditedEntity {
    @Column(nullable = false)
    private Long expertId;
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int dayOfWeek;
    @Column(nullable = false)
    private LocalTime startLocal;
    @Column(nullable = false)
    private LocalTime endLocal;
    @Column(nullable = false)
    private String timezone;
    @Column(nullable = false)
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
