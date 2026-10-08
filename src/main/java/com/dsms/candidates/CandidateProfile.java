package com.dsms.candidates;

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
@Table(name = "candidate_profile")
public class CandidateProfile extends AuditedEntity {
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String displayCode;
    @Column(nullable = false)
    private String fullName;
    private String phone;
    private String email;
    private String countryCode;
    private Integer experienceYears;
    private String supportGoals;
    private String preferredDays;
    private String preferredTimes;
    private String employerLabel;
    private String whatsappGroupName;
    private String whatsappGroupLink;
    private Long primarySkillId;
    private String confidentialNotes;
    private Long coordinatorUserId;
    private LocalDate onboardingDate;
    private LocalDate supportStartDate;
    private String operationalNotes;
    @Column(nullable = false)
    private String status;

    @ManyToMany
    @JoinTable(name = "candidate_skill",
            joinColumns = @JoinColumn(name = "candidate_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<SkillEntity> skills = new HashSet<>();
}
