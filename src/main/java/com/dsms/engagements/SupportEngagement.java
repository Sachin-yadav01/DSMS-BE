package com.dsms.engagements;

import com.dsms.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "support_engagement")
public class SupportEngagement extends AuditedEntity {
    @Column(nullable = false)
    private String displayCode;
    @Column(nullable = false)
    private Long candidateId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private Long coordinatorUserId;
    @Column(nullable = false)
    private LocalDate startDate;
    private LocalDate endDate;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private String scheduleTimezone;
    private String meetingPlatform;
    private String defaultMeetingUrl;
    private String sharedInstructions;
    private Long currentTermsId;

    @ManyToMany
    @JoinTable(name = "engagement_skill",
            joinColumns = @JoinColumn(name = "engagement_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<com.dsms.candidates.SkillEntity> skills = new HashSet<>();
}
