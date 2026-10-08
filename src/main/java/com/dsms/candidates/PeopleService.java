package com.dsms.candidates;

import com.dsms.audit.AuditService;
import com.dsms.common.api.Ids;
import com.dsms.common.api.PageResponse;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.domain.UserRole;
import com.dsms.common.domain.UserStatus;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.ExpertAssignment;
import com.dsms.engagements.ExpertAssignmentRepositoryBridge;
import com.dsms.engagements.SupportEngagement;
import com.dsms.engagements.SupportEngagementQuery;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import com.dsms.users.UserAccount;
import com.dsms.users.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Repository
interface ExpertSkillRepository extends JpaRepository<ExpertSkill, ExpertSkillId> {
    List<ExpertSkill> findByIdExpertId(Long expertId);
}

@Repository
interface ExpertAvailabilityRuleRepository extends JpaRepository<ExpertAvailabilityRule, Long> {
    List<ExpertAvailabilityRule> findByExpertId(Long expertId);
}

@Repository
interface ExpertAvailabilityExceptionRepository extends JpaRepository<ExpertAvailabilityException, Long> {
    List<ExpertAvailabilityException> findByExpertId(Long expertId);
}

record SkillDto(String id, String name, String status) {}
record CandidateDto(String id, String displayCode, String userId, String fullName, String phone, String email, String status,
                    String coordinatorUserId, Integer experienceYears, String supportGoals, String preferredDays,
                    String preferredTimes, String employerLabel, String consultancyName, String whatsappGroupName,
                    String whatsappGroupLink, String primarySkillId, String technology, List<String> technologies,
                    String confidentialNotes, List<String> skillIds, String onboardingDate, String supportStartDate,
                    String primarySupporterId, String primarySupporterName, String thisMonthHours) {}
record ExpertDto(String id, String displayCode, String userId, String fullName, String phone, String email, String status,
                 Integer experienceYears, String profileSummary, String notes, String hourlyPayoutRate, String paymentType,
                 String workingTime, List<ExpertSkillDto> skills, List<String> technologies, String primaryTechnology,
                 int activeCandidateCount, String todayHours, String monthHours) {}
record ExpertSkillDto(String skillId, String skillName, String proficiency) {}
record AvailabilityRuleDto(String id, int dayOfWeek, String startLocal, String endLocal, String timezone, String effectiveFrom, String effectiveTo) {}
record MatchDto(ExpertDto expert, List<String> reasons, int workload) {}

record SkillRequest(String name) {}
record CandidateRequest(String userId, String fullName, String phone, String email, String coordinatorUserId,
                        Integer experienceYears, String supportGoals, String preferredDays, String preferredTimes,
                        String employerLabel, String consultancyName, String whatsappGroupName, String whatsappGroupLink,
                        String primarySkillId, List<String> skillIds, String onboardingDate, String status,
                        String operationalNotes) {}
record ExpertRequest(String userId, String fullName, String phone, String email, Integer experienceYears,
                     String profileSummary, String notes, String hourlyPayoutRate, String paymentType, String workingTime,
                     String status, List<String> skillIds, List<ExpertSkillDto> skills) {}
record AvailabilityRuleRequest(int dayOfWeek, String startLocal, String endLocal, String timezone, String effectiveFrom, String effectiveTo) {}

@Service
public class PeopleService {
    private final SkillRepository skills;
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final ExpertSkillRepository expertSkills;
    private final ExpertAvailabilityRuleRepository availabilityRules;
    private final ExpertAvailabilityExceptionRepository availabilityExceptions;
    private final UserAccountRepository users;
    private final DisplayCodeService codes;
    private final CurrentUser currentUser;
    private final DsmsProperties properties;
    private final AuditService auditService;
    private final WorkEntryRepository workEntries;
    private final SupportEngagementQuery engagements;
    private final ExpertAssignmentRepositoryBridge assignments;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;

    PeopleService(SkillRepository skills, CandidateProfileRepository candidates, ExpertProfileRepository experts,
                  ExpertSkillRepository expertSkills, ExpertAvailabilityRuleRepository availabilityRules,
                  ExpertAvailabilityExceptionRepository availabilityExceptions, UserAccountRepository users,
                  DisplayCodeService codes, CurrentUser currentUser, DsmsProperties properties, AuditService auditService,
                  WorkEntryRepository workEntries, SupportEngagementQuery engagements,
                  ExpertAssignmentRepositoryBridge assignments, Clock clock, PasswordEncoder passwordEncoder) {
        this.skills = skills;
        this.candidates = candidates;
        this.experts = experts;
        this.expertSkills = expertSkills;
        this.availabilityRules = availabilityRules;
        this.availabilityExceptions = availabilityExceptions;
        this.users = users;
        this.codes = codes;
        this.currentUser = currentUser;
        this.properties = properties;
        this.auditService = auditService;
        this.workEntries = workEntries;
        this.engagements = engagements;
        this.assignments = assignments;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SkillDto createSkill(SkillRequest request) {
        requireStaff();
        String normalized = request.name().trim().toLowerCase();
        if (skills.existsByNormalizedName(normalized)) {
            throw Exceptions.conflict("SKILL_EXISTS", "Skill already exists");
        }
        SkillEntity skill = new SkillEntity();
        skill.setName(request.name().trim());
        skill.setNormalizedName(normalized);
        skill.setStatus("ACTIVE");
        skills.save(skill);
        return toSkill(skill);
    }

    @Transactional(readOnly = true)
    public List<SkillDto> searchSkills(String q) {
        if (q == null || q.isBlank()) {
            return skills.findByStatus("ACTIVE").stream().map(this::toSkill).toList();
        }
        return skills.findByStatusAndNameContainingIgnoreCase("ACTIVE", q).stream().map(this::toSkill).toList();
    }

    @Transactional
    public SkillDto archiveSkill(String id) {
        requireAdmin();
        SkillEntity skill = skills.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Skill not found"));
        skill.setStatus("ARCHIVED");
        return toSkill(skill);
    }

    @Transactional
    public CandidateDto createCandidate(CandidateRequest request) {
        requireStaff();
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw Exceptions.badRequest("FULL_NAME_REQUIRED", "Full name is required");
        }
        if (request.whatsappGroupName() == null || request.whatsappGroupName().isBlank()) {
            throw Exceptions.badRequest("WHATSAPP_GROUP_REQUIRED", "WhatsApp group name is required");
        }
        UserAccount user;
        if (request.userId() == null || request.userId().isBlank()) {
            user = provisionCandidateUser(request.fullName(), request.email());
        } else {
            user = users.findById(Ids.parse(request.userId())).orElseThrow(() -> Exceptions.notFound("User not found"));
            if (user.getRole() != UserRole.CANDIDATE) {
                throw Exceptions.badRequest("INVALID_ROLE", "User is not a candidate");
            }
            if (candidates.findByUserId(user.getId()).isPresent()) {
                throw Exceptions.conflict("PROFILE_EXISTS", "Candidate profile already exists");
            }
        }
        CandidateProfile profile = new CandidateProfile();
        profile.setUserId(user.getId());
        profile.setDisplayCode(codes.next("CAN"));
        applyCandidateFields(profile, request, true);
        profile.setStatus("NOT_STARTED");
        candidates.save(profile);
        auditService.record("CANDIDATE_CREATE", "candidate_profile", Ids.toJson(profile.getId()), null,
                Map.of("code", profile.getDisplayCode(), "status", profile.getStatus()));
        return toCandidate(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateDto> listCandidates(int page, int size, String q, String status, String technology,
                                                     String supporterId, String startDateFrom) {
        List<CandidateDto> all = candidates.findAll().stream()
                .filter(this::canViewCandidate)
                .map(this::toCandidate)
                .filter(c -> q == null || q.isBlank()
                        || c.fullName().toLowerCase().contains(q.toLowerCase())
                        || c.displayCode().toLowerCase().contains(q.toLowerCase())
                        || (c.whatsappGroupName() != null && c.whatsappGroupName().toLowerCase().contains(q.toLowerCase()))
                        || (c.consultancyName() != null && c.consultancyName().toLowerCase().contains(q.toLowerCase())))
                .filter(c -> status == null || status.isBlank() || status.equals(c.status()))
                .filter(c -> technology == null || technology.isBlank()
                        || (c.technology() != null && c.technology().equalsIgnoreCase(technology))
                        || (c.technologies() != null && c.technologies().stream().anyMatch(t -> t.equalsIgnoreCase(technology))))
                .filter(c -> supporterId == null || supporterId.isBlank()
                        || Objects.equals(supporterId, c.primarySupporterId()))
                .filter(c -> startDateFrom == null || startDateFrom.isBlank()
                        || (c.supportStartDate() != null && !LocalDate.parse(c.supportStartDate()).isBefore(LocalDate.parse(startDateFrom))))
                .toList();
        int from = Math.max(page, 0) * Math.min(Math.max(size, 1), 100);
        int to = Math.min(from + Math.min(Math.max(size, 1), 100), all.size());
        List<CandidateDto> items = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResponse.of(items, all.size(), Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @Transactional(readOnly = true)
    public CandidateDto getCandidate(String id) {
        CandidateProfile profile = candidates.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Candidate not found"));
        if (!canViewCandidate(profile)) {
            throw Exceptions.forbidden("Cannot view this candidate");
        }
        return toCandidate(profile);
    }

    @Transactional
    public CandidateDto updateCandidate(String id, CandidateRequest request) {
        CandidateProfile profile = candidates.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Candidate not found"));
        if (!currentUser.isAdmin() && !(currentUser.isCoordinator() && currentUser.requireUserId().equals(profile.getCoordinatorUserId()))) {
            throw Exceptions.forbidden("Cannot update this candidate");
        }
        String before = profile.getStatus();
        applyCandidateFields(profile, request, false);
        if (request.status() != null && !request.status().isBlank() && currentUser.isAdmin()) {
            validateCandidateStatus(request.status());
            profile.setStatus(request.status());
        }
        auditService.record("CANDIDATE_UPDATE", "candidate_profile", id, Map.of("status", before),
                Map.of("status", profile.getStatus()));
        return toCandidate(profile);
    }

    @Transactional
    public ExpertDto createExpert(ExpertRequest request) {
        requireStaff();
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw Exceptions.badRequest("FULL_NAME_REQUIRED", "Full name is required");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw Exceptions.badRequest("PHONE_REQUIRED", "Phone number is required");
        }
        if (request.hourlyPayoutRate() == null || request.hourlyPayoutRate().isBlank()) {
            throw Exceptions.badRequest("RATE_REQUIRED", "Rate is required");
        }
        UserAccount user;
        if (request.userId() == null || request.userId().isBlank()) {
            user = provisionExpertUser(request.fullName(), request.email());
        } else {
            user = users.findById(Ids.parse(request.userId())).orElseThrow(() -> Exceptions.notFound("User not found"));
            if (user.getRole() != UserRole.EXPERT) {
                throw Exceptions.badRequest("INVALID_ROLE", "User is not an expert");
            }
            if (experts.findByUserId(user.getId()).isPresent()) {
                throw Exceptions.conflict("PROFILE_EXISTS", "Expert profile already exists");
            }
        }
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(user.getId());
        profile.setDisplayCode(codes.next("EXP"));
        profile.setFullName(request.fullName().trim());
        profile.setPhone(request.phone().trim());
        profile.setEmail(request.email() == null ? user.getEmail() : request.email());
        profile.setExperienceYears(request.experienceYears());
        profile.setProfileSummary(request.profileSummary());
        profile.setNotes(request.notes());
        profile.setHourlyPayoutRate(new BigDecimal(request.hourlyPayoutRate()));
        profile.setCurrency(properties.getCurrency());
        profile.setPaymentType(normalizePaymentType(request.paymentType()));
        profile.setWorkingTime(normalizeWorkingTime(request.workingTime()));
        profile.setStatus(normalizeExpertStatus(request.status() == null ? "ACTIVE" : request.status()));
        experts.save(profile);
        replaceExpertSkills(profile.getId(), resolveExpertSkillDtos(request));
        auditService.record("EXPERT_CREATE", "expert_profile", Ids.toJson(profile.getId()), null, Map.of("code", profile.getDisplayCode()));
        return toExpert(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpertDto> listExperts(int page, int size, String q, String workingTime) {
        String needle = q == null || q.isBlank() ? null : q.toLowerCase(Locale.ROOT);
        String workingFilter = workingTime == null || workingTime.isBlank() ? null : normalizeWorkingTime(workingTime);
        List<ExpertDto> all = experts.findAll().stream()
                .map(this::toExpert)
                .filter(e -> workingFilter == null || workingFilter.equals(e.workingTime()))
                .filter(e -> {
                    if (needle == null) {
                        return true;
                    }
                    boolean nameMatch = e.fullName() != null && e.fullName().toLowerCase(Locale.ROOT).contains(needle);
                    boolean codeMatch = e.displayCode() != null && e.displayCode().toLowerCase(Locale.ROOT).contains(needle);
                    boolean phoneMatch = e.phone() != null && e.phone().toLowerCase(Locale.ROOT).contains(needle);
                    boolean techMatch = e.technologies() != null && e.technologies().stream()
                            .anyMatch(t -> t != null && t.toLowerCase(Locale.ROOT).contains(needle));
                    boolean primaryMatch = e.primaryTechnology() != null && e.primaryTechnology().toLowerCase(Locale.ROOT).contains(needle);
                    return nameMatch || codeMatch || phoneMatch || techMatch || primaryMatch;
                })
                .sorted(Comparator.comparing(ExpertDto::fullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNo = Math.max(page, 0);
        int from = pageNo * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<ExpertDto> items = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResponse.of(items, all.size(), pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public ExpertDto getExpert(String id) {
        ExpertProfile profile = experts.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Expert not found"));
        return toExpert(profile);
    }

    @Transactional
    public ExpertDto updateExpert(String id, ExpertRequest request) {
        ExpertProfile profile = experts.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Expert not found"));
        boolean own = currentUser.isExpert() && currentUser.requireUserId().equals(profile.getUserId());
        if (!currentUser.isAdmin() && !currentUser.isCoordinator() && !own) {
            throw Exceptions.forbidden("Cannot update this expert");
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            profile.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            if (request.phone().isBlank()) {
                throw Exceptions.badRequest("PHONE_REQUIRED", "Phone number is required");
            }
            profile.setPhone(request.phone().trim());
        }
        if (request.email() != null) {
            profile.setEmail(request.email());
        }
        if (request.experienceYears() != null) {
            profile.setExperienceYears(request.experienceYears());
        }
        if (request.profileSummary() != null) {
            profile.setProfileSummary(request.profileSummary());
        }
        if (request.notes() != null) {
            profile.setNotes(request.notes());
        }
        if ((currentUser.isAdmin() || currentUser.isCoordinator() || own) && request.hourlyPayoutRate() != null
                && !request.hourlyPayoutRate().isBlank()) {
            profile.setHourlyPayoutRate(new BigDecimal(request.hourlyPayoutRate()));
        }
        if ((currentUser.isAdmin() || currentUser.isCoordinator()) && request.paymentType() != null) {
            profile.setPaymentType(normalizePaymentType(request.paymentType()));
        }
        if ((currentUser.isAdmin() || currentUser.isCoordinator()) && request.workingTime() != null) {
            profile.setWorkingTime(normalizeWorkingTime(request.workingTime()));
        }
        if (currentUser.isAdmin() && request.status() != null) {
            profile.setStatus(normalizeExpertStatus(request.status()));
        }
        if (request.skills() != null || request.skillIds() != null) {
            replaceExpertSkills(profile.getId(), resolveExpertSkillDtos(request));
        }
        auditService.record("EXPERT_UPDATE", "expert_profile", id, null, Map.of("code", profile.getDisplayCode()));
        return toExpert(profile);
    }

    @Transactional
    public AvailabilityRuleDto addAvailability(String expertId, AvailabilityRuleRequest request) {
        ExpertProfile profile = experts.findById(Ids.parse(expertId)).orElseThrow(() -> Exceptions.notFound("Expert not found"));
        assertCanEditExpert(profile);
        ExpertAvailabilityRule rule = new ExpertAvailabilityRule();
        rule.setExpertId(profile.getId());
        rule.setDayOfWeek(request.dayOfWeek());
        rule.setStartLocal(LocalTime.parse(request.startLocal()));
        rule.setEndLocal(LocalTime.parse(request.endLocal()));
        rule.setTimezone(request.timezone());
        rule.setEffectiveFrom(LocalDate.parse(request.effectiveFrom()));
        rule.setEffectiveTo(request.effectiveTo() == null || request.effectiveTo().isBlank() ? null : LocalDate.parse(request.effectiveTo()));
        availabilityRules.save(rule);
        return toRule(rule);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRuleDto> listAvailability(String expertId) {
        experts.findById(Ids.parse(expertId)).orElseThrow(() -> Exceptions.notFound("Expert not found"));
        return availabilityRules.findByExpertId(Ids.parse(expertId)).stream().map(this::toRule).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchDto> match(List<String> skillIds, String timezone, String status) {
        requireStaff();
        Set<Long> wanted = skillIds == null ? Set.of() : skillIds.stream().map(Ids::parse).collect(Collectors.toSet());
        List<MatchDto> matches = new ArrayList<>();
        for (ExpertProfile expert : experts.findAll()) {
            String normalized = normalizeExpertStatus(expert.getStatus());
            if (status != null && !status.isBlank() && !status.equals(normalized) && !status.equals(expert.getStatus())) {
                continue;
            }
            if (timezone != null && !timezone.isBlank()) {
                boolean tzMatch = availabilityRules.findByExpertId(expert.getId()).stream().anyMatch(r -> timezone.equals(r.getTimezone()));
                if (!tzMatch && !"ACTIVE".equals(normalized)) {
                    continue;
                }
            }
            List<ExpertSkill> owned = expertSkills.findByIdExpertId(expert.getId());
            Set<Long> ownedIds = owned.stream().map(s -> s.getId().getSkillId()).collect(Collectors.toSet());
            List<String> reasons = new ArrayList<>();
            long overlap = wanted.stream().filter(ownedIds::contains).count();
            if (!wanted.isEmpty() && overlap == 0) {
                continue;
            }
            if (overlap > 0) {
                reasons.add(overlap + " matching skill(s)");
            }
            if (!availabilityRules.findByExpertId(expert.getId()).isEmpty()) {
                reasons.add("Has published weekly availability");
            }
            reasons.add("Status " + normalized);
            matches.add(new MatchDto(toExpert(expert), reasons, owned.size()));
        }
        return matches;
    }

    public CandidateProfile requireCandidateEntity(Long id) {
        return candidates.findById(id).orElseThrow(() -> Exceptions.notFound("Candidate not found"));
    }

    public ExpertProfile requireExpertEntity(Long id) {
        return experts.findById(id).orElseThrow(() -> Exceptions.notFound("Expert not found"));
    }

    public void markCandidateActive(CandidateProfile profile, LocalDate supportStartDate) {
        profile.setStatus("ACTIVE");
        if (supportStartDate != null) {
            profile.setSupportStartDate(supportStartDate);
        } else if (profile.getSupportStartDate() == null) {
            profile.setSupportStartDate(LocalDate.now(clock));
        }
        candidates.save(profile);
    }

    public boolean canViewCandidate(CandidateProfile profile) {
        if (currentUser.isAdmin()) {
            return true;
        }
        if (currentUser.isCoordinator()) {
            return currentUser.requireUserId().equals(profile.getCoordinatorUserId());
        }
        if (currentUser.isCandidate()) {
            return currentUser.requireUserId().equals(profile.getUserId());
        }
        if (currentUser.isExpert()) {
            ExpertProfile expert = experts.findByUserId(currentUser.requireUserId()).orElse(null);
            if (expert == null) {
                return false;
            }
            return engagements.findAll().stream()
                    .filter(e -> e.getCandidateId().equals(profile.getId()))
                    .anyMatch(e -> assignments.findAll().stream()
                            .anyMatch(a -> a.getEngagementId().equals(e.getId()) && a.getExpertId().equals(expert.getId())));
        }
        return false;
    }

    private void applyCandidateFields(CandidateProfile profile, CandidateRequest request, boolean creating) {
        if (request.fullName() != null && !request.fullName().isBlank()) {
            profile.setFullName(request.fullName().trim());
        } else if (creating) {
            throw Exceptions.badRequest("FULL_NAME_REQUIRED", "Full name is required");
        }
        if (request.phone() != null) {
            profile.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.email() != null) {
            profile.setEmail(request.email().isBlank() ? null : request.email().trim());
        }
        if (creating || currentUser.isAdmin() || currentUser.isCoordinator()) {
            if (creating || request.coordinatorUserId() != null) {
                profile.setCoordinatorUserId(resolveCoordinator(request.coordinatorUserId()));
            }
        }
        if (request.experienceYears() != null) {
            profile.setExperienceYears(request.experienceYears());
        }
        if (request.supportGoals() != null) {
            profile.setSupportGoals(request.supportGoals());
        }
        if (request.preferredDays() != null) {
            profile.setPreferredDays(request.preferredDays());
        }
        if (request.preferredTimes() != null) {
            profile.setPreferredTimes(request.preferredTimes());
        }
        if (request.consultancyName() != null || request.employerLabel() != null) {
            String consultancy = firstNonBlank(request.consultancyName(), request.employerLabel());
            profile.setEmployerLabel(consultancy == null || consultancy.isBlank() ? null : consultancy);
        }
        if (request.whatsappGroupName() != null) {
            if (request.whatsappGroupName().isBlank()) {
                if (creating) {
                    throw Exceptions.badRequest("WHATSAPP_GROUP_REQUIRED", "WhatsApp group name is required");
                }
            } else {
                profile.setWhatsappGroupName(request.whatsappGroupName().trim());
            }
        } else if (creating) {
            throw Exceptions.badRequest("WHATSAPP_GROUP_REQUIRED", "WhatsApp group name is required");
        }
        if (request.whatsappGroupLink() != null) {
            profile.setWhatsappGroupLink(request.whatsappGroupLink().isBlank() ? null : request.whatsappGroupLink().trim());
        }
        if (request.operationalNotes() != null) {
            profile.setOperationalNotes(request.operationalNotes());
        }
        if (request.onboardingDate() != null && !request.onboardingDate().isBlank()) {
            profile.setOnboardingDate(LocalDate.parse(request.onboardingDate()));
        } else if (creating) {
            profile.setOnboardingDate(LocalDate.now(clock));
        }
        if (request.skillIds() != null && !request.skillIds().isEmpty()) {
            Set<SkillEntity> loaded = loadSkills(request.skillIds());
            profile.setSkills(loaded);
            if (request.primarySkillId() != null && !request.primarySkillId().isBlank()) {
                Long skillId = Ids.parse(request.primarySkillId());
                skills.findById(skillId).orElseThrow(() -> Exceptions.notFound("Technology not found"));
                profile.setPrimarySkillId(skillId);
            } else {
                profile.setPrimarySkillId(loaded.stream().findFirst().map(SkillEntity::getId).orElse(null));
            }
        } else if (request.primarySkillId() != null && !request.primarySkillId().isBlank()) {
            Long skillId = Ids.parse(request.primarySkillId());
            skills.findById(skillId).orElseThrow(() -> Exceptions.notFound("Technology not found"));
            profile.setPrimarySkillId(skillId);
            if (creating) {
                profile.setSkills(loadSkills(List.of(Ids.toJson(skillId))));
            }
        }
    }

    private UserAccount provisionCandidateUser(String fullName, String email) {
        return provisionUser(fullName, email, UserRole.CANDIDATE, "candidates");
    }

    private UserAccount provisionExpertUser(String fullName, String email) {
        return provisionUser(fullName, email, UserRole.EXPERT, "supporters");
    }

    private UserAccount provisionUser(String fullName, String email, UserRole role, String localPartDomain) {
        String normalizedEmail;
        if (email != null && !email.isBlank()) {
            normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            if (users.existsByEmailNormalized(normalizedEmail)) {
                throw Exceptions.conflict("EMAIL_IN_USE", "A user with that email already exists");
            }
        } else {
            String slug = fullName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("^\\.|\\.$", "");
            if (slug.isBlank()) {
                slug = role == UserRole.EXPERT ? "supporter" : "candidate";
            }
            normalizedEmail = slug + "+" + clock.millis() + "@" + localPartDomain + ".dsms.local";
        }
        UserAccount account = new UserAccount();
        account.setEmail(normalizedEmail);
        account.setEmailNormalized(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        account.setDisplayName(fullName.trim());
        account.setRole(role);
        account.setStatus(UserStatus.INVITED);
        account.setTimezone(properties.getTimezone());
        account.setMustChangePassword(true);
        return users.save(account);
    }

    private List<ExpertSkillDto> resolveExpertSkillDtos(ExpertRequest request) {
        if (request.skills() != null) {
            return request.skills();
        }
        if (request.skillIds() == null) {
            return List.of();
        }
        List<ExpertSkillDto> dtos = new ArrayList<>();
        boolean first = true;
        for (String skillId : request.skillIds()) {
            dtos.add(new ExpertSkillDto(skillId, null, first ? "PRIMARY" : "INTERMEDIATE"));
            first = false;
        }
        return dtos;
    }

    private String normalizeWorkingTime(String workingTime) {
        if (workingTime == null || workingTime.isBlank()) {
            return "BOTH";
        }
        String normalized = workingTime.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MORNING", "EVENING", "BOTH").contains(normalized)) {
            throw Exceptions.badRequest("INVALID_WORKING_TIME", "Working time must be Morning, Evening, or Both");
        }
        return normalized;
    }

    private String normalizePaymentType(String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            return "HOURLY";
        }
        String normalized = paymentType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "HOURLY", "PER_HOUR", "HOUR" -> "HOURLY";
            case "MONTHLY", "PER_MONTH", "MONTH", "FIXED_MONTHLY" -> "MONTHLY";
            default -> throw Exceptions.badRequest("INVALID_PAYMENT_TYPE", "Rate unit must be hourly or monthly");
        };
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private Long resolveCoordinator(String coordinatorUserId) {
        if (currentUser.isCoordinator() && coordinatorUserId == null) {
            return currentUser.requireUserId();
        }
        if (coordinatorUserId == null) {
            return currentUser.requireUserId();
        }
        UserAccount coordinator = users.findById(Ids.parse(coordinatorUserId)).orElseThrow(() -> Exceptions.notFound("Coordinator not found"));
        if (coordinator.getRole() != UserRole.COORDINATOR && coordinator.getRole() != UserRole.ADMIN) {
            throw Exceptions.badRequest("INVALID_ROLE", "Coordinator is invalid");
        }
        return coordinator.getId();
    }

    private Set<SkillEntity> loadSkills(List<String> skillIds) {
        if (skillIds == null) {
            return new HashSet<>();
        }
        return new HashSet<>(skills.findAllById(skillIds.stream().map(Ids::parse).toList()));
    }

    private void replaceExpertSkills(Long expertId, List<ExpertSkillDto> dtos) {
        expertSkills.deleteAll(expertSkills.findByIdExpertId(expertId));
        if (dtos == null) {
            return;
        }
        boolean primarySet = false;
        for (ExpertSkillDto dto : dtos) {
            ExpertSkill skill = new ExpertSkill();
            ExpertSkillId id = new ExpertSkillId();
            id.setExpertId(expertId);
            id.setSkillId(Ids.parse(dto.skillId()));
            skill.setId(id);
            String proficiency = dto.proficiency() == null ? "INTERMEDIATE" : dto.proficiency();
            if ("PRIMARY".equals(proficiency)) {
                if (primarySet) {
                    proficiency = "ADVANCED";
                } else {
                    primarySet = true;
                }
            }
            skill.setProficiency(proficiency);
            expertSkills.save(skill);
        }
    }

    private void requireStaff() {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Staff access required");
        }
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admin access required");
        }
    }

    private void assertCanEditExpert(ExpertProfile profile) {
        boolean own = currentUser.isExpert() && currentUser.requireUserId().equals(profile.getUserId());
        if (!currentUser.isAdmin() && !currentUser.isCoordinator() && !own) {
            throw Exceptions.forbidden("Cannot edit this expert");
        }
    }

    private void validateCandidateStatus(String status) {
        if (!Set.of("NOT_STARTED", "ACTIVE", "ON_HOLD", "COMPLETED", "INACTIVE").contains(status)) {
            throw Exceptions.badRequest("INVALID_STATUS", "Unsupported candidate status");
        }
    }

    private String normalizeExpertStatus(String status) {
        return switch (status) {
            case "AVAILABLE" -> "ACTIVE";
            case "FULLY_BOOKED" -> "UNAVAILABLE";
            case "ACTIVE", "UNAVAILABLE", "INACTIVE" -> status;
            default -> throw Exceptions.badRequest("INVALID_STATUS", "Unsupported expert status");
        };
    }

    private SkillDto toSkill(SkillEntity skill) {
        return new SkillDto(Ids.toJson(skill.getId()), skill.getName(), skill.getStatus());
    }

    private CandidateDto toCandidate(CandidateProfile profile) {
        String confidential = null;
        if (currentUser.isAdmin() || (currentUser.isCoordinator() && currentUser.requireUserId().equals(profile.getCoordinatorUserId()))) {
            confidential = profile.getConfidentialNotes();
        }
        String technology = null;
        if (profile.getPrimarySkillId() != null) {
            technology = skills.findById(profile.getPrimarySkillId()).map(SkillEntity::getName).orElse(null);
        } else if (!profile.getSkills().isEmpty()) {
            technology = profile.getSkills().iterator().next().getName();
        }
        SupporterRef supporter = resolvePrimarySupporter(profile.getId());
        String monthHours = formatHours(monthSecondsForCandidate(profile.getId()));
        List<String> technologies = profile.getSkills().stream().map(SkillEntity::getName).sorted().toList();
        if (technologies.isEmpty() && technology != null) {
            technologies = List.of(technology);
        }
        return new CandidateDto(Ids.toJson(profile.getId()), profile.getDisplayCode(), Ids.toJson(profile.getUserId()),
                profile.getFullName(), profile.getPhone(), profile.getEmail(), profile.getStatus(),
                Ids.toJson(profile.getCoordinatorUserId()), profile.getExperienceYears(), profile.getSupportGoals(),
                profile.getPreferredDays(), profile.getPreferredTimes(), profile.getEmployerLabel(),
                profile.getEmployerLabel(), profile.getWhatsappGroupName(), profile.getWhatsappGroupLink(),
                profile.getPrimarySkillId() == null ? null : Ids.toJson(profile.getPrimarySkillId()), technology,
                technologies, confidential, profile.getSkills().stream().map(s -> Ids.toJson(s.getId())).toList(),
                profile.getOnboardingDate() == null ? null : profile.getOnboardingDate().toString(),
                profile.getSupportStartDate() == null ? null : profile.getSupportStartDate().toString(),
                supporter == null ? null : supporter.id(), supporter == null ? null : supporter.name(), monthHours);
    }

    private ExpertDto toExpert(ExpertProfile profile) {
        boolean canSeeRate = currentUser.isAdmin() || currentUser.isCoordinator()
                || (currentUser.isExpert() && currentUser.requireUserId().equals(profile.getUserId()));
        String rate = canSeeRate ? profile.getHourlyPayoutRate().toPlainString() : null;
        Map<Long, String> skillNames = skills.findAll().stream().collect(Collectors.toMap(SkillEntity::getId, SkillEntity::getName, (a, b) -> a));
        List<ExpertSkillDto> skillDtos = expertSkills.findByIdExpertId(profile.getId()).stream()
                .map(s -> new ExpertSkillDto(Ids.toJson(s.getId().getSkillId()), skillNames.get(s.getId().getSkillId()), s.getProficiency()))
                .toList();
        List<String> technologies = skillDtos.stream()
                .map(ExpertSkillDto::skillName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String primaryTech = skillDtos.stream().filter(s -> "PRIMARY".equals(s.proficiency())).map(ExpertSkillDto::skillName).findFirst()
                .orElse(technologies.isEmpty() ? null : technologies.getFirst());
        int activeCount = (int) activeCandidateIdsForExpert(profile.getId()).size();
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int todaySeconds = workEntries.findByExpertIdOrderByCreatedOnDesc(profile.getId()).stream()
                .filter(e -> overlapsDay(e, today, zone))
                .mapToInt(WorkEntry::getRecordedSeconds)
                .sum();
        int monthSeconds = workEntries.findByExpertIdOrderByCreatedOnDesc(profile.getId()).stream()
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(YearMonth.from(today)))
                .mapToInt(WorkEntry::getRecordedSeconds)
                .sum();
        String workingTime = profile.getWorkingTime() == null || profile.getWorkingTime().isBlank()
                ? "BOTH"
                : profile.getWorkingTime();
        return new ExpertDto(Ids.toJson(profile.getId()), profile.getDisplayCode(), Ids.toJson(profile.getUserId()),
                profile.getFullName(), profile.getPhone(), profile.getEmail(), normalizeExpertStatus(profile.getStatus()),
                profile.getExperienceYears(), profile.getProfileSummary(), profile.getNotes(), rate,
                profile.getPaymentType() == null ? "HOURLY" : profile.getPaymentType(), workingTime, skillDtos,
                technologies, primaryTech, activeCount, formatHours(todaySeconds), formatHours(monthSeconds));
    }

    private record SupporterRef(String id, String name) {}

    private SupporterRef resolvePrimarySupporter(Long candidateId) {
        Instant now = clock.instant();
        for (SupportEngagement engagement : engagements.findAll()) {
            if (!engagement.getCandidateId().equals(candidateId) || !"ACTIVE".equals(engagement.getStatus())) {
                continue;
            }
            for (ExpertAssignment assignment : assignments.findAll()) {
                if (!assignment.getEngagementId().equals(engagement.getId())) {
                    continue;
                }
                if (!"ACCEPTED".equals(assignment.getAcceptanceState())) {
                    continue;
                }
                if (now.isBefore(assignment.getEffectiveStart())) {
                    continue;
                }
                if (assignment.getEffectiveEnd() != null && !now.isBefore(assignment.getEffectiveEnd())) {
                    continue;
                }
                return experts.findById(assignment.getExpertId())
                        .map(e -> new SupporterRef(Ids.toJson(e.getId()), e.getFullName()))
                        .orElse(null);
            }
        }
        return null;
    }

    private Set<Long> activeCandidateIdsForExpert(Long expertId) {
        Instant now = clock.instant();
        Set<Long> engagementIds = assignments.findAll().stream()
                .filter(a -> a.getExpertId().equals(expertId))
                .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()))
                .filter(a -> !now.isBefore(a.getEffectiveStart()))
                .filter(a -> a.getEffectiveEnd() == null || now.isBefore(a.getEffectiveEnd()))
                .map(ExpertAssignment::getEngagementId)
                .collect(Collectors.toSet());
        return engagements.findAll().stream()
                .filter(e -> engagementIds.contains(e.getId()))
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .map(SupportEngagement::getCandidateId)
                .collect(Collectors.toSet());
    }

    private int monthSecondsForCandidate(Long candidateId) {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        YearMonth month = YearMonth.now(clock.withZone(zone));
        return workEntries.findByCandidateIdOrderByCreatedOnDesc(candidateId).stream()
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month))
                .mapToInt(WorkEntry::getRecordedSeconds)
                .sum();
    }

    private boolean overlapsDay(WorkEntry entry, LocalDate day, ZoneId zone) {
        return entry.getCreatedOn().atZone(zone).toLocalDate().equals(day);
    }

    private String formatHours(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h + "h " + String.format("%02dm", m);
    }

    private AvailabilityRuleDto toRule(ExpertAvailabilityRule rule) {
        return new AvailabilityRuleDto(Ids.toJson(rule.getId()), rule.getDayOfWeek(), rule.getStartLocal().toString(),
                rule.getEndLocal().toString(), rule.getTimezone(), rule.getEffectiveFrom().toString(),
                rule.getEffectiveTo() == null ? null : rule.getEffectiveTo().toString());
    }
}

@RestController
@RequestMapping("/api/v1/skills")
class SkillController {
    private final PeopleService people;
    SkillController(PeopleService people) { this.people = people; }

    @GetMapping
    public List<SkillDto> search(@RequestParam(required = false) String q) { return people.searchSkills(q); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SkillDto create(@RequestBody SkillRequest request) { return people.createSkill(request); }

    @PostMapping("/{id}/archive")
    public SkillDto archive(@PathVariable String id) { return people.archiveSkill(id); }
}

@RestController
@RequestMapping("/api/v1/candidates")
class CandidateController {
    private final PeopleService people;
    CandidateController(PeopleService people) { this.people = people; }

    @GetMapping
    public PageResponse<CandidateDto> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String technology,
                                           @RequestParam(required = false) String supporterId,
                                           @RequestParam(required = false) String startDateFrom) {
        return people.listCandidates(page, size, q, status, technology, supporterId, startDateFrom);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDto create(@RequestBody CandidateRequest request) { return people.createCandidate(request); }

    @GetMapping("/{id}")
    public CandidateDto get(@PathVariable String id) { return people.getCandidate(id); }

    @PutMapping("/{id}")
    public CandidateDto update(@PathVariable String id, @RequestBody CandidateRequest request) { return people.updateCandidate(id, request); }
}

@RestController
@RequestMapping("/api/v1/experts")
class ExpertController {
    private final PeopleService people;
    ExpertController(PeopleService people) { this.people = people; }

    @GetMapping
    public PageResponse<ExpertDto> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String workingTime) {
        return people.listExperts(page, size, q, workingTime);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpertDto create(@RequestBody ExpertRequest request) { return people.createExpert(request); }

    @GetMapping("/{id}")
    public ExpertDto get(@PathVariable String id) { return people.getExpert(id); }

    @PutMapping("/{id}")
    public ExpertDto update(@PathVariable String id, @RequestBody ExpertRequest request) { return people.updateExpert(id, request); }

    @GetMapping("/{id}/availability")
    public List<AvailabilityRuleDto> availability(@PathVariable String id) { return people.listAvailability(id); }

    @PostMapping("/{id}/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityRuleDto addAvailability(@PathVariable String id, @RequestBody AvailabilityRuleRequest request) {
        return people.addAvailability(id, request);
    }

    @GetMapping("/matching")
    public List<MatchDto> matching(@RequestParam(required = false) List<String> skillIds,
                                   @RequestParam(required = false) String timezone,
                                   @RequestParam(required = false) String status) {
        return people.match(skillIds, timezone, status);
    }
}
