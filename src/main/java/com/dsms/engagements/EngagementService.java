package com.dsms.engagements;

import com.dsms.audit.AuditService;
import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.candidates.PeopleService;
import com.dsms.common.api.Ids;
import com.dsms.common.api.PageResponse;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.common.security.CurrentUser;
import com.dsms.notifications.NotificationService;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
interface SupportEngagementRepository extends JpaRepository<SupportEngagement, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SupportEngagement e where e.id = :id")
    java.util.Optional<SupportEngagement> lockById(@Param("id") Long id);

    List<SupportEngagement> findByCandidateId(Long candidateId);
    List<SupportEngagement> findByCoordinatorUserId(Long coordinatorUserId);
}

@Repository
interface ExpertAssignmentRepository extends JpaRepository<ExpertAssignment, Long> {
    List<ExpertAssignment> findByEngagementIdOrderByEffectiveStartDesc(Long engagementId);
    List<ExpertAssignment> findByExpertId(Long expertId);
    java.util.Optional<ExpertAssignment> findFirstByEngagementIdAndAcceptanceStateAndEffectiveEndIsNull(Long engagementId, String state);
}

@Repository
interface AssignmentEventRepository extends JpaRepository<AssignmentEvent, Long> {
    List<AssignmentEvent> findByAssignmentIdOrderByCreatedOnAsc(Long assignmentId);
}

@Repository
interface EngagementStatusHistoryRepository extends JpaRepository<EngagementStatusHistory, Long> {}

record EngagementDto(String id, String displayCode, String candidateId, String candidateName, String groupName,
                            String title, String coordinatorUserId, String startDate, String endDate, String status,
                            String scheduleTimezone, String meetingPlatform, String defaultMeetingUrl, String currentTermsId,
                            String planType, String candidateFee, String supporterRate, String primaryExpertId,
                            String primaryExpertName, String thisMonthHours) {}
record TermsDto(String id, String planType, String currency, String hourlyRate, String monthlyBaseFee,
                       Integer includedSeconds, boolean overageEnabled, String overageHourlyRate, String status, String effectiveFrom) {}
record AssignmentDto(String id, String engagementId, String expertId, String effectiveStart, String effectiveEnd,
                            String acceptanceState, String hourlyRateSnapshot) {}
record EngagementRequest(String candidateId, String title, String coordinatorUserId, String startDate, String endDate,
                                String scheduleTimezone, String meetingPlatform, String defaultMeetingUrl, List<String> skillIds) {}
record TermsRequest(String planType, String hourlyRate, String monthlyBaseFee, Integer includedSeconds, boolean overageEnabled,
                           String overageHourlyRate, String evidenceNote, String effectiveFrom) {}
record AssignmentRequest(String expertId, String effectiveStart, String hourlyRateSnapshot) {}
record ReplaceAssignmentRequest(String expertId, String effectiveStart, String hourlyRateSnapshot, String reason) {}
record ActivateSupportRequest(String candidateId, String title, String startDate, String primaryExpertId,
                              String backupExpertId, String expectedTiming, String meetingPlatform, String meetingUrl,
                              String supportPlan, String candidateFee, String supporterRate, String planType, String notes) {}

@Service
public class EngagementService {
    private final SupportEngagementRepository engagements;
    private final EngagementTermsRepository terms;
    private final ExpertAssignmentRepository assignments;
    private final AssignmentEventRepository assignmentEvents;
    private final EngagementStatusHistoryRepository statusHistory;
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final PeopleService peopleService;
    private final DisplayCodeService codes;
    private final CurrentUser currentUser;
    private final DsmsProperties properties;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final WorkEntryRepository workEntries;
    private final Clock clock;

    public EngagementService(SupportEngagementRepository engagements, EngagementTermsRepository terms,
                             ExpertAssignmentRepository assignments, AssignmentEventRepository assignmentEvents,
                             EngagementStatusHistoryRepository statusHistory, CandidateProfileRepository candidates,
                             ExpertProfileRepository experts, PeopleService peopleService, DisplayCodeService codes,
                             CurrentUser currentUser, DsmsProperties properties, AuditService auditService,
                             NotificationService notifications, WorkEntryRepository workEntries, Clock clock) {
        this.engagements = engagements;
        this.terms = terms;
        this.assignments = assignments;
        this.assignmentEvents = assignmentEvents;
        this.statusHistory = statusHistory;
        this.candidates = candidates;
        this.experts = experts;
        this.peopleService = peopleService;
        this.codes = codes;
        this.currentUser = currentUser;
        this.properties = properties;
        this.auditService = auditService;
        this.notifications = notifications;
        this.workEntries = workEntries;
        this.clock = clock;
    }

    @Transactional
    public EngagementDto create(EngagementRequest request) {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Staff access required");
        }
        CandidateProfile candidate = peopleService.requireCandidateEntity(Ids.parse(request.candidateId()));
        if (!peopleService.canViewCandidate(candidate) && !currentUser.isAdmin()) {
            throw Exceptions.forbidden("Cannot create engagement for this candidate");
        }
        SupportEngagement engagement = new SupportEngagement();
        engagement.setDisplayCode(codes.next("ENG"));
        engagement.setCandidateId(candidate.getId());
        engagement.setTitle(request.title());
        engagement.setCoordinatorUserId(request.coordinatorUserId() == null ? currentUser.requireUserId() : Ids.parse(request.coordinatorUserId()));
        engagement.setStartDate(LocalDate.parse(request.startDate()));
        engagement.setEndDate(request.endDate() == null || request.endDate().isBlank() ? null : LocalDate.parse(request.endDate()));
        engagement.setStatus("DRAFT");
        engagement.setScheduleTimezone(request.scheduleTimezone() == null ? properties.getTimezone() : request.scheduleTimezone());
        engagement.setMeetingPlatform(request.meetingPlatform());
        engagement.setDefaultMeetingUrl(request.defaultMeetingUrl());
        engagements.save(engagement);
        recordStatus(engagement, null, "DRAFT", "Created");
        return toDto(engagement);
    }

    @Transactional(readOnly = true)
    public PageResponse<EngagementDto> list(int page, int size, String q, String status) {
        List<EngagementDto> all = engagements.findAll().stream()
                .filter(this::canView)
                .map(this::toDto)
                .filter(e -> status == null || status.isBlank() || status.equalsIgnoreCase(e.status()))
                .filter(e -> {
                    if (q == null || q.isBlank()) {
                        return true;
                    }
                    String needle = q.toLowerCase(Locale.ROOT);
                    return (e.candidateName() != null && e.candidateName().toLowerCase(Locale.ROOT).contains(needle))
                            || (e.groupName() != null && e.groupName().toLowerCase(Locale.ROOT).contains(needle))
                            || (e.title() != null && e.title().toLowerCase(Locale.ROOT).contains(needle))
                            || (e.primaryExpertName() != null && e.primaryExpertName().toLowerCase(Locale.ROOT).contains(needle))
                            || (e.displayCode() != null && e.displayCode().toLowerCase(Locale.ROOT).contains(needle));
                })
                .sorted(Comparator.comparing(EngagementDto::startDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNo = Math.max(page, 0);
        int from = pageNo * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<EngagementDto> items = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResponse.of(items, all.size(), pageNo, pageSize);
    }

    @Transactional(readOnly = true)
    public EngagementDto get(String id) {
        return toDto(requireVisible(Ids.parse(id)));
    }

    @Transactional
    public TermsDto saveTerms(String engagementId, TermsRequest request) {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Staff access required to configure commercial terms");
        }
        SupportEngagement engagement = lock(Ids.parse(engagementId));
        EngagementTerms entity = new EngagementTerms();
        entity.setEngagementId(engagement.getId());
        entity.setPlanType(request.planType());
        entity.setCurrency(properties.getCurrency());
        entity.setHourlyRate(parseMoney(request.hourlyRate()));
        entity.setMonthlyBaseFee(parseMoney(request.monthlyBaseFee()));
        entity.setIncludedSeconds(request.includedSeconds());
        entity.setOverageEnabled(request.overageEnabled());
        entity.setOverageHourlyRate(parseMoney(request.overageHourlyRate()));
        entity.setEvidenceNote(request.evidenceNote());
        entity.setStatus("APPROVED");
        entity.setEffectiveFrom(request.effectiveFrom() == null ? LocalDate.now() : LocalDate.parse(request.effectiveFrom()));
        terms.save(entity);
        engagement.setCurrentTermsId(entity.getId());
        auditService.record("TERMS_APPROVE", "engagement_terms", Ids.toJson(entity.getId()), null, Map.of("plan", request.planType()));
        return toTerms(entity);
    }

    @Transactional
    public EngagementDto activate(String id) {
        SupportEngagement engagement = lock(Ids.parse(id));
        assertStaffOwned(engagement);
        if (engagement.getCurrentTermsId() == null) {
            throw Exceptions.conflict("TERMS_REQUIRED", "Approved commercial terms are required before activation");
        }
        boolean covered = assignments.findByEngagementIdOrderByEffectiveStartDesc(engagement.getId()).stream()
                .anyMatch(a -> "ACCEPTED".equals(a.getAcceptanceState()) && covers(a, clock.instant()));
        if (!covered) {
            throw Exceptions.conflict("ASSIGNMENT_REQUIRED", "An accepted expert assignment is required before activation");
        }
        String from = engagement.getStatus();
        engagement.setStatus("ACTIVE");
        recordStatus(engagement, from, "ACTIVE", "Activated");
        CandidateProfile candidate = peopleService.requireCandidateEntity(engagement.getCandidateId());
        peopleService.markCandidateActive(candidate, engagement.getStartDate());
        auditService.record("SUPPORT_ACTIVATE", "support_engagement", Ids.toJson(engagement.getId()),
                Map.of("from", from), Map.of("to", "ACTIVE"));
        return toDto(engagement);
    }

    @Transactional
    public EngagementDto activateSupport(ActivateSupportRequest request) {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Staff access required");
        }
        if (request.candidateId() == null || request.candidateId().isBlank()) {
            throw Exceptions.badRequest("CANDIDATE_REQUIRED", "Candidate is required");
        }
        if (request.primaryExpertId() == null || request.primaryExpertId().isBlank()) {
            throw Exceptions.badRequest("SUPPORTER_REQUIRED", "Primary supporter is required");
        }
        if (request.candidateFee() == null || request.candidateFee().isBlank()) {
            throw Exceptions.badRequest("FEE_REQUIRED", "Candidate fee is required");
        }
        if (request.supporterRate() == null || request.supporterRate().isBlank()) {
            throw Exceptions.badRequest("RATE_REQUIRED", "Supporter rate is required");
        }
        CandidateProfile candidate = peopleService.requireCandidateEntity(Ids.parse(request.candidateId()));
        if (!"NOT_STARTED".equals(candidate.getStatus()) && !"ON_HOLD".equals(candidate.getStatus())
                && !"INACTIVE".equals(candidate.getStatus()) && !"ACTIVE".equals(candidate.getStatus())) {
            throw Exceptions.conflict("INVALID_CANDIDATE_STATUS", "Candidate cannot be activated from status " + candidate.getStatus());
        }
        if (engagements.findByCandidateId(candidate.getId()).stream()
                .anyMatch(e -> "ACTIVE".equals(e.getStatus()) || "SCHEDULED".equals(e.getStatus()))) {
            throw Exceptions.conflict("ENGAGEMENT_EXISTS", "Candidate already has an active or scheduled engagement");
        }
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = request.startDate() == null || request.startDate().isBlank() ? today : LocalDate.parse(request.startDate());
        String title = request.title() == null || request.title().isBlank()
                ? candidate.getFullName() + " support"
                : request.title();
        String platform = inferMeetingPlatform(request.meetingPlatform(), request.meetingUrl());
        EngagementDto created = create(new EngagementRequest(
                request.candidateId(),
                title,
                null,
                start.toString(),
                null,
                properties.getTimezone(),
                platform,
                request.meetingUrl(),
                null
        ));
        SupportEngagement engagement = lock(Ids.parse(created.id()));
        if (request.notes() != null && !request.notes().isBlank()) {
            candidate.setOperationalNotes(request.notes());
        }
        if (request.expectedTiming() != null && !request.expectedTiming().isBlank()) {
            candidate.setPreferredTimes(request.expectedTiming());
        }
        if (request.supportPlan() != null && !request.supportPlan().isBlank()) {
            candidate.setSupportGoals(request.supportPlan());
        } else if (request.notes() != null && !request.notes().isBlank()) {
            candidate.setSupportGoals(request.notes());
        }
        String requestedPlan = request.planType() == null || request.planType().isBlank() ? "HOURLY" : request.planType();
        String termsPlan = switch (requestedPlan) {
            case "FIXED_MONTHLY", "PER_CANDIDATE", "MONTHLY" -> "MONTHLY";
            default -> "HOURLY";
        };
        saveTerms(created.id(), new TermsRequest(
                termsPlan,
                request.candidateFee(),
                "MONTHLY".equals(termsPlan) ? request.candidateFee() : null,
                null,
                false,
                null,
                request.notes(),
                start.toString()
        ));
        ExpertProfile primary = peopleService.requireExpertEntity(Ids.parse(request.primaryExpertId()));
        ExpertAssignment assignment = new ExpertAssignment();
        assignment.setEngagementId(engagement.getId());
        assignment.setExpertId(primary.getId());
        assignment.setEffectiveStart(start.atStartOfDay(zone).toInstant());
        assignment.setAcceptanceState("ACCEPTED");
        assignment.setHourlyRateSnapshot(new BigDecimal(request.supporterRate()));
        assignment.setCurrency(properties.getCurrency());
        assignments.save(assignment);
        addEvent(assignment.getId(), "ACCEPTED", "Primary supporter assigned on activation");
        if (request.backupExpertId() != null && !request.backupExpertId().isBlank()) {
            ExpertProfile backup = peopleService.requireExpertEntity(Ids.parse(request.backupExpertId()));
            ExpertAssignment backupAssignment = new ExpertAssignment();
            backupAssignment.setEngagementId(engagement.getId());
            backupAssignment.setExpertId(backup.getId());
            backupAssignment.setEffectiveStart(start.atStartOfDay(zone).toInstant());
            backupAssignment.setAcceptanceState("PROPOSED");
            backupAssignment.setHourlyRateSnapshot(backup.getHourlyPayoutRate());
            backupAssignment.setCurrency(properties.getCurrency());
            backupAssignment.setReplacementReason("BACKUP");
            assignments.save(backupAssignment);
            addEvent(backupAssignment.getId(), "PROPOSED", "Backup supporter recorded");
        }
        String from = engagement.getStatus();
        String targetStatus = start.isAfter(today) ? "SCHEDULED" : "ACTIVE";
        engagement.setStatus(targetStatus);
        recordStatus(engagement, from, targetStatus, "Activated via support activation");
        if ("ACTIVE".equals(targetStatus)) {
            peopleService.markCandidateActive(candidate, start);
        }
        auditService.record("SUPPORT_ACTIVATE", "support_engagement", Ids.toJson(engagement.getId()), null,
                Map.of("candidateId", request.candidateId(), "primaryExpertId", request.primaryExpertId(), "status", targetStatus));
        notifications.notifyUser(primary.getUserId(),
                "ACTIVE".equals(targetStatus) ? "Support activated" : "Support scheduled",
                engagement.getTitle(), "/daily-sheet");
        return toDto(engagement);
    }

    @Transactional
    public EngagementDto pause(String id, String reason) {
        SupportEngagement engagement = lock(Ids.parse(id));
        assertStaffOwned(engagement);
        if (!"ACTIVE".equals(engagement.getStatus())) {
            throw Exceptions.conflict("INVALID_STATUS", "Only active engagements can be paused");
        }
        String from = engagement.getStatus();
        engagement.setStatus("PAUSED");
        recordStatus(engagement, from, "PAUSED", reason);
        return toDto(engagement);
    }

    @Transactional
    public EngagementDto resume(String id, String reason) {
        SupportEngagement engagement = lock(Ids.parse(id));
        assertStaffOwned(engagement);
        if (!"PAUSED".equals(engagement.getStatus()) && !"SCHEDULED".equals(engagement.getStatus())) {
            throw Exceptions.conflict("INVALID_STATUS", "Only paused or scheduled engagements can be resumed");
        }
        String from = engagement.getStatus();
        engagement.setStatus("ACTIVE");
        recordStatus(engagement, from, "ACTIVE", reason == null ? "Resumed" : reason);
        CandidateProfile candidate = peopleService.requireCandidateEntity(engagement.getCandidateId());
        peopleService.markCandidateActive(candidate, engagement.getStartDate());
        return toDto(engagement);
    }

    @Transactional
    public EngagementDto complete(String id, String reason) {
        SupportEngagement engagement = lock(Ids.parse(id));
        assertStaffOwned(engagement);
        String from = engagement.getStatus();
        engagement.setStatus("COMPLETED");
        recordStatus(engagement, from, "COMPLETED", reason);
        return toDto(engagement);
    }

    @Transactional
    public AssignmentDto propose(String engagementId, AssignmentRequest request) {
        SupportEngagement engagement = lock(Ids.parse(engagementId));
        assertStaffOwned(engagement);
        ExpertProfile expert = peopleService.requireExpertEntity(Ids.parse(request.expertId()));
        Instant start = request.effectiveStart() == null ? clock.instant() : Instant.parse(request.effectiveStart());
        assertNoOverlap(engagement.getId(), start, null, null);
        ExpertAssignment assignment = new ExpertAssignment();
        assignment.setEngagementId(engagement.getId());
        assignment.setExpertId(expert.getId());
        assignment.setEffectiveStart(start);
        assignment.setAcceptanceState("PROPOSED");
        assignment.setHourlyRateSnapshot(request.hourlyRateSnapshot() == null ? expert.getHourlyPayoutRate() : new BigDecimal(request.hourlyRateSnapshot()));
        assignment.setCurrency(properties.getCurrency());
        assignments.save(assignment);
        addEvent(assignment.getId(), "PROPOSED", "Proposed assignment");
        notifications.notifyUser(expert.getUserId(), "New assignment proposed", engagement.getTitle(), "/assignments/" + assignment.getId());
        return toAssignment(assignment);
    }

    @Transactional
    public AssignmentDto accept(String assignmentId) {
        ExpertAssignment assignment = assignments.findById(Ids.parse(assignmentId)).orElseThrow(() -> Exceptions.notFound("Assignment not found"));
        ExpertProfile expert = peopleService.requireExpertEntity(assignment.getExpertId());
        if (!currentUser.isAdmin() && !currentUser.requireUserId().equals(expert.getUserId())) {
            throw Exceptions.forbidden("Cannot accept this assignment");
        }
        SupportEngagement engagement = lock(assignment.getEngagementId());
        assertNoOverlap(engagement.getId(), assignment.getEffectiveStart(), assignment.getEffectiveEnd(), assignment.getId());
        assignment.setAcceptanceState("ACCEPTED");
        addEvent(assignment.getId(), "ACCEPTED", "Expert accepted");
        notifications.notifyUser(engagement.getCoordinatorUserId(), "Assignment accepted", engagement.getTitle(), "/engagements/" + engagement.getId());
        return toAssignment(assignment);
    }

    @Transactional
    public AssignmentDto decline(String assignmentId, String reason) {
        ExpertAssignment assignment = assignments.findById(Ids.parse(assignmentId)).orElseThrow(() -> Exceptions.notFound("Assignment not found"));
        ExpertProfile expert = peopleService.requireExpertEntity(assignment.getExpertId());
        if (!currentUser.isAdmin() && !currentUser.requireUserId().equals(expert.getUserId())) {
            throw Exceptions.forbidden("Cannot decline this assignment");
        }
        assignment.setAcceptanceState("DECLINED");
        assignment.setReplacementReason(reason);
        addEvent(assignment.getId(), "DECLINED", reason);
        return toAssignment(assignment);
    }

    @Transactional
    public AssignmentDto replace(String engagementId, ReplaceAssignmentRequest request) {
        SupportEngagement engagement = lock(Ids.parse(engagementId));
        assertStaffOwned(engagement);
        Instant when = request.effectiveStart() == null ? clock.instant() : Instant.parse(request.effectiveStart());
        ExpertAssignment current = assignments.findByEngagementIdOrderByEffectiveStartDesc(engagement.getId()).stream()
                .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()) && covers(a, when))
                .findFirst()
                .orElseThrow(() -> Exceptions.conflict("NO_CURRENT_ASSIGNMENT", "No accepted assignment to replace"));
        current.setEffectiveEnd(when);
        addEvent(current.getId(), "REPLACED", request.reason());
        ExpertProfile expert = peopleService.requireExpertEntity(Ids.parse(request.expertId()));
        ExpertAssignment next = new ExpertAssignment();
        next.setEngagementId(engagement.getId());
        next.setExpertId(expert.getId());
        next.setEffectiveStart(when);
        next.setAcceptanceState("PROPOSED");
        next.setHourlyRateSnapshot(request.hourlyRateSnapshot() == null ? expert.getHourlyPayoutRate() : new BigDecimal(request.hourlyRateSnapshot()));
        next.setCurrency(properties.getCurrency());
        next.setReplacementReason(request.reason());
        assignments.save(next);
        addEvent(next.getId(), "PROPOSED", "Replacement proposed: " + request.reason());
        auditService.record("ASSIGNMENT_REPLACE", "expert_assignment", Ids.toJson(next.getId()), Map.of("from", current.getId()), Map.of("to", next.getId()));
        notifications.notifyUser(expert.getUserId(), "Replacement assignment proposed", engagement.getTitle(), "/assignments/" + next.getId());
        return toAssignment(next);
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> history(String engagementId) {
        requireVisible(Ids.parse(engagementId));
        return assignments.findByEngagementIdOrderByEffectiveStartDesc(Ids.parse(engagementId)).stream().map(this::toAssignment).toList();
    }

    public SupportEngagement requireVisible(Long id) {
        SupportEngagement engagement = engagements.findById(id).orElseThrow(() -> Exceptions.notFound("Engagement not found"));
        if (!canView(engagement)) {
            throw Exceptions.forbidden("Cannot view this engagement");
        }
        return engagement;
    }

    public SupportEngagement lock(Long id) {
        return engagements.lockById(id).orElseThrow(() -> Exceptions.notFound("Engagement not found"));
    }

    public ExpertAssignment requireAcceptedCovering(Long engagementId, Instant instant) {
        return assignments.findByEngagementIdOrderByEffectiveStartDesc(engagementId).stream()
                .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()) && covers(a, instant))
                .findFirst()
                .orElseThrow(() -> Exceptions.conflict("NO_COVERAGE", "No accepted assignment covers this time"));
    }

    public boolean canView(SupportEngagement engagement) {
        if (currentUser.isAdmin()) {
            return true;
        }
        if (currentUser.isCoordinator()) {
            return currentUser.requireUserId().equals(engagement.getCoordinatorUserId());
        }
        if (currentUser.isCandidate()) {
            CandidateProfile candidate = candidates.findById(engagement.getCandidateId()).orElse(null);
            return candidate != null && currentUser.requireUserId().equals(candidate.getUserId());
        }
        if (currentUser.isExpert()) {
            ExpertProfile expert = experts.findByUserId(currentUser.requireUserId()).orElse(null);
            if (expert == null) {
                return false;
            }
            return assignments.findByEngagementIdOrderByEffectiveStartDesc(engagement.getId()).stream()
                    .anyMatch(a -> a.getExpertId().equals(expert.getId()));
        }
        return false;
    }

    private void assertStaffOwned(SupportEngagement engagement) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isCoordinator() && currentUser.requireUserId().equals(engagement.getCoordinatorUserId())) {
            return;
        }
        throw Exceptions.forbidden("Cannot manage this engagement");
    }

    private void assertNoOverlap(Long engagementId, Instant start, Instant end, Long ignoreId) {
        boolean overlap = assignments.findByEngagementIdOrderByEffectiveStartDesc(engagementId).stream()
                .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()))
                .filter(a -> ignoreId == null || !ignoreId.equals(a.getId()))
                .anyMatch(a -> overlaps(a.getEffectiveStart(), a.getEffectiveEnd(), start, end));
        if (overlap) {
            throw Exceptions.conflict("ASSIGNMENT_OVERLAP", "An accepted expert already covers this period");
        }
    }

    public static boolean covers(ExpertAssignment assignment, Instant instant) {
        if (instant.isBefore(assignment.getEffectiveStart())) {
            return false;
        }
        return assignment.getEffectiveEnd() == null || instant.isBefore(assignment.getEffectiveEnd());
    }

    static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        Instant aFinish = aEnd == null ? Instant.MAX : aEnd;
        Instant bFinish = bEnd == null ? Instant.MAX : bEnd;
        return aStart.isBefore(bFinish) && bStart.isBefore(aFinish);
    }

    private void recordStatus(SupportEngagement engagement, String from, String to, String reason) {
        EngagementStatusHistory history = new EngagementStatusHistory();
        history.setEngagementId(engagement.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setActorUserId(currentUser.principal().map(p -> p.getId()).orElse(null));
        history.setCreatedOn(clock.instant());
        statusHistory.save(history);
    }

    private void addEvent(Long assignmentId, String type, String note) {
        AssignmentEvent event = new AssignmentEvent();
        event.setAssignmentId(assignmentId);
        event.setEventType(type);
        event.setNote(note);
        event.setActorUserId(currentUser.principal().map(p -> p.getId()).orElse(null));
        event.setCreatedOn(clock.instant());
        assignmentEvents.save(event);
    }

    private BigDecimal parseMoney(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private EngagementDto toDto(SupportEngagement engagement) {
        EngagementTerms term = engagement.getCurrentTermsId() == null ? null
                : terms.findById(engagement.getCurrentTermsId()).orElse(null);
        String plan = term == null ? null : term.getPlanType();
        String candidateFee = null;
        if (term != null) {
            candidateFee = "MONTHLY".equals(term.getPlanType())
                    ? money(term.getMonthlyBaseFee() != null ? term.getMonthlyBaseFee() : term.getHourlyRate())
                    : money(term.getHourlyRate());
        }
        CandidateProfile candidate = candidates.findById(engagement.getCandidateId()).orElse(null);
        ExpertAssignment covering = assignments.findByEngagementIdOrderByEffectiveStartDesc(engagement.getId()).stream()
                .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()))
                .filter(a -> a.getEffectiveEnd() == null)
                .findFirst()
                .orElseGet(() -> assignments.findByEngagementIdOrderByEffectiveStartDesc(engagement.getId()).stream()
                        .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()))
                        .findFirst()
                        .orElse(null));
        ExpertProfile expert = covering == null ? null : experts.findById(covering.getExpertId()).orElse(null);
        ZoneId zone = ZoneId.of(properties.getTimezone());
        YearMonth month = YearMonth.now(clock.withZone(zone));
        int monthSeconds = workEntries.findByCandidateIdOrderByCreatedOnDesc(engagement.getCandidateId()).stream()
                .filter(e -> Objects.equals(e.getEngagementId(), engagement.getId()))
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month))
                .mapToInt(WorkEntry::getRecordedSeconds)
                .sum();
        return new EngagementDto(
                Ids.toJson(engagement.getId()),
                engagement.getDisplayCode(),
                Ids.toJson(engagement.getCandidateId()),
                candidate == null ? null : candidate.getFullName(),
                candidate == null ? null : candidate.getWhatsappGroupName(),
                engagement.getTitle(),
                Ids.toJson(engagement.getCoordinatorUserId()),
                engagement.getStartDate().toString(),
                engagement.getEndDate() == null ? null : engagement.getEndDate().toString(),
                engagement.getStatus(),
                engagement.getScheduleTimezone(),
                engagement.getMeetingPlatform(),
                engagement.getDefaultMeetingUrl(),
                Ids.toJson(engagement.getCurrentTermsId()),
                plan,
                candidateFee,
                covering == null ? null : money(covering.getHourlyRateSnapshot()),
                expert == null ? null : Ids.toJson(expert.getId()),
                expert == null ? null : expert.getFullName(),
                formatHours(monthSeconds)
        );
    }

    private String inferMeetingPlatform(String platform, String url) {
        if (platform != null && !platform.isBlank()) {
            return platform;
        }
        if (url == null || url.isBlank()) {
            return "OTHER";
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("meet.google.com")) {
            return "GOOGLE_MEET";
        }
        if (lower.contains("zoom.us")) {
            return "ZOOM";
        }
        return "OTHER";
    }

    private String formatHours(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h + "h " + String.format("%02dm", m);
    }

    private TermsDto toTerms(EngagementTerms entity) {
        return new TermsDto(Ids.toJson(entity.getId()), entity.getPlanType(), entity.getCurrency(),
                money(entity.getHourlyRate()), money(entity.getMonthlyBaseFee()), entity.getIncludedSeconds(),
                entity.isOverageEnabled(), money(entity.getOverageHourlyRate()), entity.getStatus(), entity.getEffectiveFrom().toString());
    }

    private AssignmentDto toAssignment(ExpertAssignment assignment) {
        return new AssignmentDto(Ids.toJson(assignment.getId()), Ids.toJson(assignment.getEngagementId()), Ids.toJson(assignment.getExpertId()),
                assignment.getEffectiveStart().toString(), assignment.getEffectiveEnd() == null ? null : assignment.getEffectiveEnd().toString(),
                assignment.getAcceptanceState(), assignment.getHourlyRateSnapshot().toPlainString());
    }

    private String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}

@RestController
@RequestMapping("/api/v1/engagements")
class EngagementController {
    private final EngagementService service;
    EngagementController(EngagementService service) { this.service = service; }

    @GetMapping
    public PageResponse<EngagementDto> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String status) {
        return service.list(page, size, q, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EngagementDto create(@RequestBody EngagementRequest request) { return service.create(request); }

    @PostMapping("/activate-support")
    @ResponseStatus(HttpStatus.CREATED)
    public EngagementDto activateSupport(@RequestBody ActivateSupportRequest request) {
        return service.activateSupport(request);
    }

    @GetMapping("/{id}")
    public EngagementDto get(@PathVariable String id) { return service.get(id); }

    @PostMapping("/{id}/terms")
    public TermsDto terms(@PathVariable String id, @RequestBody TermsRequest request) { return service.saveTerms(id, request); }

    @PostMapping("/{id}/activate")
    public EngagementDto activate(@PathVariable String id) { return service.activate(id); }

    @PostMapping("/{id}/pause")
    public EngagementDto pause(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.pause(id, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{id}/resume")
    public EngagementDto resume(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.resume(id, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{id}/complete")
    public EngagementDto complete(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.complete(id, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentDto propose(@PathVariable String id, @RequestBody AssignmentRequest request) { return service.propose(id, request); }

    @GetMapping("/{id}/assignments")
    public List<AssignmentDto> history(@PathVariable String id) { return service.history(id); }

    @PostMapping("/{id}/assignments/replace")
    public AssignmentDto replace(@PathVariable String id, @RequestBody ReplaceAssignmentRequest request) { return service.replace(id, request); }
}

@RestController
@RequestMapping("/api/v1/assignments")
class AssignmentController {
    private final EngagementService service;
    AssignmentController(EngagementService service) { this.service = service; }

    @PostMapping("/{id}/accept")
    public AssignmentDto accept(@PathVariable String id) { return service.accept(id); }

    @PostMapping("/{id}/decline")
    public AssignmentDto decline(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.decline(id, body == null ? null : body.get("reason"));
    }
}
