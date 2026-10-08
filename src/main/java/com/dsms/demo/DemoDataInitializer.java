package com.dsms.demo;

import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.candidates.SkillEntity;
import com.dsms.candidates.SkillRepository;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.engagements.EngagementTerms;
import com.dsms.engagements.EngagementTermsRepository;
import com.dsms.engagements.ExpertAssignment;
import com.dsms.engagements.SupportEngagement;
import com.dsms.users.UserAccount;
import com.dsms.users.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(10)
public class DemoDataInitializer implements ApplicationRunner {
    private final UserAccountRepository users;
    private final SkillRepository skills;
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final org.springframework.data.jpa.repository.JpaRepository<SupportEngagement, Long> engagements;
    private final EngagementTermsRepository terms;
    private final org.springframework.data.jpa.repository.JpaRepository<ExpertAssignment, Long> assignments;
    private final DisplayCodeService codes;
    private final PasswordEncoder encoder;
    private final DsmsProperties properties;
    private final Clock clock;

    public DemoDataInitializer(UserAccountRepository users, SkillRepository skills, CandidateProfileRepository candidates,
                               ExpertProfileRepository experts, com.dsms.engagements.SupportEngagementQuery engagements,
                               EngagementTermsRepository terms, com.dsms.engagements.ExpertAssignmentRepositoryBridge assignments,
                               DisplayCodeService codes, PasswordEncoder encoder, DsmsProperties properties, Clock clock) {
        this.users = users;
        this.skills = skills;
        this.candidates = candidates;
        this.experts = experts;
        this.engagements = engagements;
        this.terms = terms;
        this.assignments = assignments;
        this.codes = codes;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isDemoMode() || users.count() > 1) {
            return;
        }
        SkillEntity java = skills.findByStatus("ACTIVE").stream().filter(s -> "Java".equalsIgnoreCase(s.getName())).findFirst()
                .orElseGet(() -> skill("Java"));
        SkillEntity react = skills.findByStatus("ACTIVE").stream().filter(s -> "React".equalsIgnoreCase(s.getName())).findFirst()
                .orElseGet(() -> skill("React"));
        UserAccount coordinator = user("coordinator@dsms.local", "Priya Coordinator", UserRole.COORDINATOR);
        UserAccount candidateUser = user("candidate@dsms.local", "Arjun Candidate", UserRole.CANDIDATE);
        UserAccount expertUser = user("expert@dsms.local", "Meera Expert", UserRole.EXPERT);
        user("coordinator2@dsms.local", "Rahul Coordinator", UserRole.COORDINATOR);
        CandidateProfile candidate = new CandidateProfile();
        candidate.setUserId(candidateUser.getId());
        candidate.setDisplayCode(codes.next("CAN"));
        candidate.setFullName(candidateUser.getDisplayName());
        candidate.setCoordinatorUserId(coordinator.getId());
        candidate.setStatus("ACTIVE");
        candidate.setOnboardingDate(LocalDate.now(clock));
        candidate.setSupportStartDate(LocalDate.now(clock).minusDays(14));
        candidate.setWhatsappGroupName("Arjun Java Support");
        candidate.setWhatsappGroupLink("https://chat.whatsapp.com/demo-arjun");
        candidate.setPrimarySkillId(java.getId());
        candidate.getSkills().add(java);
        candidates.save(candidate);
        ExpertProfile expert = new ExpertProfile();
        expert.setUserId(expertUser.getId());
        expert.setDisplayCode(codes.next("EXP"));
        expert.setFullName(expertUser.getDisplayName());
        expert.setHourlyPayoutRate(new BigDecimal("600.00"));
        expert.setCurrency(properties.getCurrency());
        expert.setPaymentType("HOURLY");
        expert.setWorkingTime("EVENING");
        expert.setStatus("ACTIVE");
        expert.setProfileSummary("Backend mentoring specialist");
        experts.save(expert);
        SupportEngagement engagement = new SupportEngagement();
        engagement.setDisplayCode(codes.next("ENG"));
        engagement.setCandidateId(candidate.getId());
        engagement.setTitle("Java backend support");
        engagement.setCoordinatorUserId(coordinator.getId());
        engagement.setStartDate(LocalDate.now(clock).minusDays(14));
        engagement.setStatus("ACTIVE");
        engagement.setScheduleTimezone(properties.getTimezone());
        engagement.setMeetingPlatform("GOOGLE_MEET");
        engagement.setDefaultMeetingUrl("https://meet.google.com/aaa-bbbb-ccc");
        engagements.save(engagement);
        EngagementTerms hourly = new EngagementTerms();
        hourly.setEngagementId(engagement.getId());
        hourly.setPlanType("HOURLY");
        hourly.setCurrency(properties.getCurrency());
        hourly.setHourlyRate(new BigDecimal("1000.00"));
        hourly.setStatus("APPROVED");
        hourly.setEffectiveFrom(LocalDate.now(clock).minusDays(14));
        terms.save(hourly);
        engagement.setCurrentTermsId(hourly.getId());
        ExpertAssignment assignment = new ExpertAssignment();
        assignment.setEngagementId(engagement.getId());
        assignment.setExpertId(expert.getId());
        assignment.setEffectiveStart(Instant.now(clock).minusSeconds(86400 * 10));
        assignment.setAcceptanceState("ACCEPTED");
        assignment.setHourlyRateSnapshot(new BigDecimal("600.00"));
        assignment.setCurrency(properties.getCurrency());
        assignments.save(assignment);
        CandidateProfile unused = new CandidateProfile();
        unused.setUserId(user("candidate2@dsms.local", "Neha Candidate", UserRole.CANDIDATE).getId());
        unused.setDisplayCode(codes.next("CAN"));
        unused.setFullName("Neha Candidate");
        unused.setCoordinatorUserId(coordinator.getId());
        unused.setStatus("NOT_STARTED");
        unused.setWhatsappGroupName("Neha React Support");
        unused.setPrimarySkillId(react.getId());
        unused.getSkills().add(react);
        candidates.save(unused);
    }

    private SkillEntity skill(String name) {
        SkillEntity skill = new SkillEntity();
        skill.setName(name);
        skill.setNormalizedName(name.toLowerCase());
        skill.setStatus("ACTIVE");
        return skills.save(skill);
    }

    private UserAccount user(String email, String name, UserRole role) {
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setEmailNormalized(email);
        account.setPasswordHash(encoder.encode("Password123!"));
        account.setDisplayName(name);
        account.setRole(role);
        account.setStatus(UserStatus.ACTIVE);
        account.setTimezone(properties.getTimezone());
        account.setMustChangePassword(false);
        return users.save(account);
    }
}
