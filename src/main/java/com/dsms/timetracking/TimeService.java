package com.dsms.timetracking;

import com.dsms.audit.AuditService;
import com.dsms.billing.AllocationService;
import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.EngagementService;
import com.dsms.engagements.ExpertAssignment;
import com.dsms.engagements.SupportEngagement;
import com.dsms.notifications.NotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Repository
interface WorkSegmentRepository extends JpaRepository<WorkSegment, Long> {
    List<WorkSegment> findByWorkEntryIdOrderByStartedOnAsc(Long workEntryId);
    java.util.Optional<WorkSegment> findByOpenEntryId(Long workEntryId);
}

@Repository
interface WorkEntryRevisionRepository extends JpaRepository<WorkEntryRevision, Long> {
    List<WorkEntryRevision> findByWorkEntryIdOrderByRevisionNoAsc(Long workEntryId);
    int countByWorkEntryId(Long workEntryId);
}

@Repository
interface WorkRevisionSegmentRepository extends JpaRepository<WorkRevisionSegment, Long> {
    List<WorkRevisionSegment> findByRevisionIdOrderByStartedOnAsc(Long revisionId);
}

@Repository
interface WorkReviewEventRepository extends JpaRepository<WorkReviewEvent, Long> {
    List<WorkReviewEvent> findByWorkEntryIdOrderByCreatedOnAsc(Long workEntryId);
}

record WorkEntryDto(String id, String displayCode, String engagementId, String assignmentId, String expertId,
                           String candidateId, String candidateName, String expertName, String groupName, String workDate,
                           String startedOn, String endedOn, String source, String category, String timerState,
                           int recordedSeconds, long serverNowEpochMilli, Long currentSegmentStartEpochMilli, long version,
                           String sharedSummary, String reviewStatus, Integer billableSeconds, Integer payableSeconds,
                           Integer approvedActualSeconds) {}
record TimerCommand(String engagementId, String sessionId, String requestId, String category, String reason) {}
record StopCommand(String issueTopic, String workDone, String remarks) {}
record ManualEntryRequest(String engagementId, String expertId, String category, String summary, String reason,
                          String issueTopic, String remarks, List<IntervalDto> intervals) {}
record IntervalDto(String startedOn, String endedOn) {}
record ReviewCommand(String note, Integer billableSeconds, Integer payableSeconds) {}

@Service
public class TimeService {
    private final WorkEntryRepository entries;
    private final WorkSegmentRepository segments;
    private final WorkEntryRevisionRepository revisions;
    private final WorkRevisionSegmentRepository revisionSegments;
    private final WorkReviewEventRepository reviewEvents;
    private final ExpertProfileRepository experts;
    private final CandidateProfileRepository candidates;
    private final EngagementService engagements;
    private final CurrentUser currentUser;
    private final DisplayCodeService codes;
    private final DsmsProperties properties;
    private final Clock clock;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final AllocationService allocations;

    public TimeService(WorkEntryRepository entries, WorkSegmentRepository segments, WorkEntryRevisionRepository revisions,
                       WorkRevisionSegmentRepository revisionSegments, WorkReviewEventRepository reviewEvents,
                       ExpertProfileRepository experts, CandidateProfileRepository candidates, EngagementService engagements,
                       CurrentUser currentUser, DisplayCodeService codes, DsmsProperties properties, Clock clock,
                       AuditService auditService, NotificationService notifications, AllocationService allocations) {
        this.entries = entries;
        this.segments = segments;
        this.revisions = revisions;
        this.revisionSegments = revisionSegments;
        this.reviewEvents = reviewEvents;
        this.experts = experts;
        this.candidates = candidates;
        this.engagements = engagements;
        this.currentUser = currentUser;
        this.codes = codes;
        this.properties = properties;
        this.clock = clock;
        this.auditService = auditService;
        this.notifications = notifications;
        this.allocations = allocations;
    }

    @Transactional
    public WorkEntryDto start(TimerCommand command) {
        ExpertProfile expert = requireOwnExpert();
        experts.lockById(expert.getId()).orElseThrow();
        if (entries.findByOpenExpertId(expert.getId()).isPresent()) {
            throw Exceptions.conflict("TIMER_OPEN", "Stop the current timer before starting another");
        }
        Instant now = clock.instant();
        SupportEngagement engagement = engagements.lock(Ids.parse(command.engagementId()));
        if (!"ACTIVE".equals(engagement.getStatus())) {
            throw Exceptions.conflict("ENGAGEMENT_INACTIVE", "Cannot record work on an inactive engagement");
        }
        ExpertAssignment assignment = engagements.requireAcceptedCovering(engagement.getId(), now);
        if (!assignment.getExpertId().equals(expert.getId())) {
            throw Exceptions.forbidden("Assignment is not yours");
        }
        WorkEntry entry = new WorkEntry();
        entry.setDisplayCode(codes.next("WRK"));
        entry.setEngagementId(engagement.getId());
        entry.setAssignmentId(assignment.getId());
        entry.setExpertId(expert.getId());
        entry.setCandidateId(engagement.getCandidateId());
        entry.setSessionId(command.sessionId() == null ? null : Ids.parse(command.sessionId()));
        entry.setRequestId(command.requestId() == null ? null : Ids.parse(command.requestId()));
        entry.setSource("TIMER");
        entry.setCategory(command.category() == null ? "MEETING" : command.category());
        entry.setTimerState("RUNNING");
        entry.setRecordedSeconds(0);
        entries.saveAndFlush(entry);
        openSegment(entry.getId(), now, null);
        addReview(entry.getId(), null, "STARTED", null);
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto pause(String id, TimerCommand command) {
        WorkEntry entry = requireOwnOpen(id);
        Instant now = clock.instant();
        if (!"RUNNING".equals(entry.getTimerState())) {
            throw Exceptions.conflict("INVALID_TIMER_STATE", "Timer is not running");
        }
        closeOpenSegment(entry.getId(), now, command == null ? null : command.reason());
        entry.setTimerState("PAUSED");
        entry.setRecordedSeconds(sumClosed(entry.getId()));
        flagLong(entry);
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto resume(String id) {
        WorkEntry entry = requireOwnOpen(id);
        Instant now = clock.instant();
        if (!"PAUSED".equals(entry.getTimerState())) {
            throw Exceptions.conflict("INVALID_TIMER_STATE", "Timer is not paused");
        }
        openSegment(entry.getId(), now, null);
        entry.setTimerState("RUNNING");
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto stop(String id, StopCommand command) {
        WorkEntry entry = requireOwnOpen(id);
        Instant now = clock.instant();
        if ("RUNNING".equals(entry.getTimerState())) {
            closeOpenSegment(entry.getId(), now, null);
        }
        entry.setTimerState("STOPPED");
        entry.setRecordedSeconds(sumClosed(entry.getId()));
        if (command != null) {
            String summary = joinSummary(command.issueTopic(), command.workDone(), command.remarks());
            if (summary != null && !summary.isBlank()) {
                entry.setSharedSummary(summary);
            }
        }
        flagLong(entry);
        addReview(entry.getId(), null, "STOPPED", command == null ? null : command.workDone());
        auditService.record("SESSION_END", "work_entry", id, null, Map.of("seconds", entry.getRecordedSeconds()));
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto forceStop(String id, String reason) {
        if (!currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admin access required");
        }
        WorkEntry entry = entries.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Work entry not found"));
        Instant now = clock.instant();
        if ("RUNNING".equals(entry.getTimerState())) {
            closeOpenSegment(entry.getId(), now, reason);
        }
        entry.setTimerState("STOPPED");
        entry.setForceStopped(true);
        entry.setForceStopReason(reason);
        entry.setReviewFlag(true);
        entry.setRecordedSeconds(sumClosed(entry.getId()));
        addReview(entry.getId(), null, "FORCE_STOP", reason);
        return toDto(entry);
    }

    @Transactional(readOnly = true)
    public WorkEntryDto active() {
        ExpertProfile expert = requireOwnExpert();
        return entries.findByOpenExpertId(expert.getId()).map(this::toDto).orElseGet(() ->
                new WorkEntryDto(null, null, null, null, Ids.toJson(expert.getId()), null, null, null, null, null,
                        null, null, null, null, null, 0, clock.instant().toEpochMilli(), null, 0, null, null, null, null, null));
    }

    @Transactional
    public WorkEntryDto submit(String id, String summary) {
        WorkEntry entry = requireOwnEntry(id);
        if (entry.getTimerState() != null && !"STOPPED".equals(entry.getTimerState())) {
            throw Exceptions.conflict("TIMER_RUNNING", "Stop the timer before submitting");
        }
        if (summary == null || summary.isBlank()) {
            throw Exceptions.badRequest("SUMMARY_REQUIRED", "A shared work summary is required");
        }
        entry.setSharedSummary(summary);
        WorkEntryRevision revision = newRevision(entry, "SUBMITTED", summary, null);
        copySegmentsToRevision(entry, revision);
        entry.setEffectiveRevisionId(revision.getId());
        addReview(entry.getId(), revision.getId(), "SUBMITTED", summary);
        SupportEngagement engagement = engagements.requireVisible(entry.getEngagementId());
        notifications.notifyUser(candidatesUser(engagement), "Work awaiting review", summary, "/timesheets/" + entry.getId());
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto createManual(ManualEntryRequest request) {
        SupportEngagement engagement = engagements.lock(Ids.parse(request.engagementId()));
        if (request.intervals() == null || request.intervals().isEmpty()) {
            throw Exceptions.badRequest("INTERVALS_REQUIRED", "Manual entries need work intervals");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw Exceptions.badRequest("REASON_REQUIRED", "Manual entries need a reason");
        }
        ExpertProfile expert;
        if (currentUser.isAdmin() || currentUser.isCoordinator()) {
            if (request.expertId() == null || request.expertId().isBlank()) {
                throw Exceptions.badRequest("EXPERT_REQUIRED", "Staff manual entries require an expertId");
            }
            expert = experts.findById(Ids.parse(request.expertId())).orElseThrow(() -> Exceptions.notFound("Expert not found"));
        } else {
            expert = requireOwnExpert();
        }
        experts.lockById(expert.getId()).orElseThrow();
        List<IntervalDto> sorted = request.intervals().stream()
                .sorted(Comparator.comparing(i -> Instant.parse(i.startedOn())))
                .toList();
        Instant first = Instant.parse(sorted.getFirst().startedOn());
        ExpertAssignment assignment = engagements.requireAcceptedCovering(engagement.getId(), first);
        if (!assignment.getExpertId().equals(expert.getId()) && !currentUser.isAdmin()) {
            throw Exceptions.forbidden("Assignment is not yours");
        }
        if (!assignment.getExpertId().equals(expert.getId()) && currentUser.isAdmin()) {
            // Admin correction for historical coverage: keep requested expert but require a covering assignment for engagement time.
            assignment = engagements.requireAcceptedCovering(engagement.getId(), first);
        }
        WorkEntry entry = new WorkEntry();
        entry.setDisplayCode(codes.next("WRK"));
        entry.setEngagementId(engagement.getId());
        entry.setAssignmentId(assignment.getId());
        entry.setExpertId(expert.getId());
        entry.setCandidateId(engagement.getCandidateId());
        entry.setSource("MANUAL");
        entry.setCategory(request.category() == null ? "INVESTIGATION" : request.category());
        entry.setTimerState(null);
        entry.setManualReason(request.reason());
        String summary = request.summary();
        if (summary == null || summary.isBlank()) {
            summary = joinSummary(request.issueTopic(), null, request.remarks());
        }
        entry.setSharedSummary(summary);
        entry.setReviewFlag(true);
        entries.save(entry);
        int seconds = 0;
        Instant previousEnd = null;
        for (IntervalDto interval : sorted) {
            Instant start = Instant.parse(interval.startedOn());
            Instant end = Instant.parse(interval.endedOn());
            if (!end.isAfter(start) || end.isAfter(clock.instant())) {
                throw Exceptions.badRequest("INVALID_INTERVAL", "Intervals cannot be empty, negative, or in the future");
            }
            if (previousEnd != null && start.isBefore(previousEnd)) {
                throw Exceptions.conflict("INTERVAL_OVERLAP", "Manual intervals overlap");
            }
            WorkSegment segment = new WorkSegment();
            segment.setWorkEntryId(entry.getId());
            segment.setStartedOn(start);
            segment.setEndedOn(end);
            segment.setCreatedOn(clock.instant());
            segments.save(segment);
            seconds += (int) Duration.between(start, end).toSeconds();
            previousEnd = end;
        }
        entry.setRecordedSeconds(seconds);
        auditService.record("SESSION_MANUAL", "work_entry", Ids.toJson(entry.getId()), null,
                Map.of("reason", request.reason(), "seconds", seconds));
        return toDto(entry);
    }

    @Transactional(readOnly = true)
    public List<WorkEntryDto> sessionHistory(String from, String to, String candidateId, String expertId, String status) {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate fromDate = from == null || from.isBlank() ? null : LocalDate.parse(from);
        LocalDate toDate = to == null || to.isBlank() ? null : LocalDate.parse(to);
        return entries.findAll().stream()
                .filter(e -> {
                    try {
                        engagements.requireVisible(e.getEngagementId());
                        return true;
                    } catch (RuntimeException ex) {
                        return false;
                    }
                })
                .filter(e -> candidateId == null || candidateId.isBlank() || candidateId.equals(Ids.toJson(e.getCandidateId())))
                .filter(e -> expertId == null || expertId.isBlank() || expertId.equals(Ids.toJson(e.getExpertId())))
                .map(this::toDto)
                .filter(d -> {
                    if (fromDate == null && toDate == null) {
                        return true;
                    }
                    if (d.workDate() == null) {
                        return false;
                    }
                    LocalDate day = LocalDate.parse(d.workDate());
                    if (fromDate != null && day.isBefore(fromDate)) {
                        return false;
                    }
                    return toDate == null || !day.isAfter(toDate);
                })
                .filter(d -> {
                    if (status == null || status.isBlank()) {
                        return true;
                    }
                    if ("RUNNING".equals(status)) {
                        return "RUNNING".equals(d.timerState()) || "PAUSED".equals(d.timerState());
                    }
                    if ("COMPLETED".equals(status)) {
                        return d.timerState() == null || "STOPPED".equals(d.timerState());
                    }
                    return status.equals(d.timerState()) || status.equals(d.reviewStatus());
                })
                .sorted(Comparator.comparing(WorkEntryDto::workDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public WorkEntryDto candidateConfirm(String id, ReviewCommand command) {
        WorkEntry entry = requireCandidateEntry(id);
        WorkEntryRevision revision = effective(entry);
        if (!"SUBMITTED".equals(revision.getStatus())) {
            throw Exceptions.conflict("INVALID_REVIEW_STATE", "Entry is not waiting for candidate review");
        }
        revision.setStatus("CANDIDATE_CONFIRMED");
        addReview(entry.getId(), revision.getId(), "CANDIDATE_CONFIRMED", command == null ? null : command.note());
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto candidateDispute(String id, ReviewCommand command) {
        WorkEntry entry = requireCandidateEntry(id);
        WorkEntryRevision revision = effective(entry);
        if (!"SUBMITTED".equals(revision.getStatus())) {
            throw Exceptions.conflict("INVALID_REVIEW_STATE", "Entry is not waiting for candidate review");
        }
        revision.setStatus("DISPUTED");
        addReview(entry.getId(), revision.getId(), "DISPUTED", command.note());
        notifications.notifyUser(engagements.requireVisible(entry.getEngagementId()).getCoordinatorUserId(),
                "Work disputed", command.note(), "/timesheets/" + id);
        return toDto(entry);
    }

    @Transactional
    public WorkEntryDto approve(String id, ReviewCommand command) {
        WorkEntry entry = entries.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Work entry not found"));
        SupportEngagement engagement = engagements.requireVisible(entry.getEngagementId());
        if (!currentUser.isAdmin() && !(currentUser.isCoordinator() && currentUser.requireUserId().equals(engagement.getCoordinatorUserId()))) {
            throw Exceptions.forbidden("Cannot approve this entry");
        }
        if (currentUser.requireUserId().equals(experts.findById(entry.getExpertId()).map(ExpertProfile::getUserId).orElse(-1L))) {
            throw Exceptions.forbidden("You cannot approve your own work");
        }
        WorkEntryRevision revision = effective(entry);
        if (!"CANDIDATE_CONFIRMED".equals(revision.getStatus()) && !"DISPUTED".equals(revision.getStatus())) {
            if (!(currentUser.isAdmin() || currentUser.isCoordinator()) || command == null || command.note() == null) {
                throw Exceptions.conflict("CANDIDATE_REVIEW_REQUIRED", "Candidate confirmation is required unless an override reason is provided");
            }
        }
        int actual = revision.getProposedSeconds();
        int billable = command != null && command.billableSeconds() != null ? command.billableSeconds() : actual;
        int payable = command != null && command.payableSeconds() != null ? command.payableSeconds() : actual;
        if (billable < 0 || payable < 0 || billable > actual || payable > actual) {
            throw Exceptions.badRequest("INVALID_SECONDS", "Billable and payable seconds must be between 0 and approved actual seconds");
        }
        revision.setStatus("APPROVED");
        revision.setApprovedActualSeconds(actual);
        revision.setBillableSeconds(billable);
        revision.setPayableSeconds(payable);
        addReview(entry.getId(), revision.getId(), "APPROVED", command == null ? null : command.note());
        allocations.allocate(entry, revision, segments.findByWorkEntryIdOrderByStartedOnAsc(entry.getId()));
        auditService.record("WORK_APPROVED", "work_entry", id, null, java.util.Map.of("actual", actual, "billable", billable, "payable", payable));
        return toDto(entry);
    }

    @Transactional(readOnly = true)
    public WorkEntryDto get(String id) {
        WorkEntry entry = entries.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Work entry not found"));
        engagements.requireVisible(entry.getEngagementId());
        return toDto(entry);
    }

    @Transactional(readOnly = true)
    public List<WorkEntryDto> listMine() {
        if (currentUser.isExpert()) {
            ExpertProfile expert = requireOwnExpert();
            return entries.findByExpertIdOrderByCreatedOnDesc(expert.getId()).stream().map(this::toDto).toList();
        }
        return entries.findAll().stream()
                .filter(e -> {
                    try {
                        engagements.requireVisible(e.getEngagementId());
                        return true;
                    } catch (RuntimeException ex) {
                        return false;
                    }
                })
                .map(this::toDto)
                .toList();
    }

    public WorkEntry requireEntry(Long id) {
        return entries.findById(id).orElseThrow(() -> Exceptions.notFound("Work entry not found"));
    }

    private ExpertProfile requireOwnExpert() {
        if (!currentUser.isExpert() && !currentUser.isAdmin()) {
            throw Exceptions.forbidden("Only an expert can use the timer");
        }
        if (currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admins cannot impersonate an expert timer");
        }
        return experts.findByUserId(currentUser.requireUserId()).orElseThrow(() -> Exceptions.notFound("Expert profile not found"));
    }

    private WorkEntry requireOwnOpen(String id) {
        WorkEntry entry = requireOwnEntry(id);
        if (!"RUNNING".equals(entry.getTimerState()) && !"PAUSED".equals(entry.getTimerState())) {
            throw Exceptions.conflict("INVALID_TIMER_STATE", "Timer is not open");
        }
        return entry;
    }

    private WorkEntry requireOwnEntry(String id) {
        WorkEntry entry = entries.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Work entry not found"));
        ExpertProfile expert = requireOwnExpert();
        if (!expert.getId().equals(entry.getExpertId())) {
            throw Exceptions.forbidden("This work entry is not yours");
        }
        return entry;
    }

    private WorkEntry requireCandidateEntry(String id) {
        WorkEntry entry = entries.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Work entry not found"));
        SupportEngagement engagement = engagements.requireVisible(entry.getEngagementId());
        if (!currentUser.isCandidate() && !currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Cannot review this entry");
        }
        if (currentUser.isCandidate()) {
            com.dsms.candidates.CandidateProfileRepository unused = null;
        }
        return entry;
    }

    private WorkEntryRevision effective(WorkEntry entry) {
        if (entry.getEffectiveRevisionId() == null) {
            throw Exceptions.conflict("NOT_SUBMITTED", "Work has not been submitted");
        }
        return revisions.findById(entry.getEffectiveRevisionId()).orElseThrow();
    }

    private WorkEntryRevision newRevision(WorkEntry entry, String status, String summary, String reason) {
        WorkEntryRevision revision = new WorkEntryRevision();
        revision.setWorkEntryId(entry.getId());
        revision.setRevisionNo(revisions.countByWorkEntryId(entry.getId()) + 1);
        revision.setStatus(status);
        revision.setProposedSeconds(entry.getRecordedSeconds());
        revision.setSharedSummary(summary);
        revision.setCorrectionReason(reason);
        revision.setCreatedBy(currentUser.requireUserId());
        revision.setCreatedOn(clock.instant());
        return revisions.save(revision);
    }

    private void copySegmentsToRevision(WorkEntry entry, WorkEntryRevision revision) {
        for (WorkSegment segment : segments.findByWorkEntryIdOrderByStartedOnAsc(entry.getId())) {
            if (segment.getEndedOn() == null) {
                continue;
            }
            WorkRevisionSegment copy = new WorkRevisionSegment();
            copy.setRevisionId(revision.getId());
            copy.setStartedOn(segment.getStartedOn());
            copy.setEndedOn(segment.getEndedOn());
            revisionSegments.save(copy);
        }
    }

    private void openSegment(Long entryId, Instant now, String reason) {
        WorkSegment segment = new WorkSegment();
        segment.setWorkEntryId(entryId);
        segment.setStartedOn(now);
        segment.setPauseReason(reason);
        segment.setCreatedOn(now);
        segments.save(segment);
    }

    private void closeOpenSegment(Long entryId, Instant now, String reason) {
        WorkSegment open = segments.findByOpenEntryId(entryId).orElseThrow(() -> Exceptions.conflict("NO_OPEN_SEGMENT", "No open segment"));
        open.setEndedOn(now);
        open.setPauseReason(reason);
    }

    private int sumClosed(Long entryId) {
        return segments.findByWorkEntryIdOrderByStartedOnAsc(entryId).stream()
                .filter(s -> s.getEndedOn() != null)
                .mapToInt(s -> (int) Duration.between(s.getStartedOn(), s.getEndedOn()).toSeconds())
                .sum();
    }

    private void flagLong(WorkEntry entry) {
        if (entry.getRecordedSeconds() >= properties.getLongSessionHours() * 3600) {
            entry.setLongSessionFlag(true);
            entry.setReviewFlag(true);
        }
    }

    private void addReview(Long entryId, Long revisionId, String type, String note) {
        WorkReviewEvent event = new WorkReviewEvent();
        event.setWorkEntryId(entryId);
        event.setRevisionId(revisionId);
        event.setEventType(type);
        event.setNote(note);
        event.setActorUserId(currentUser.principal().map(p -> p.getId()).orElse(null));
        event.setCreatedOn(clock.instant());
        reviewEvents.save(event);
    }

    private Long candidatesUser(SupportEngagement engagement) {
        return currentUser.requireUserId();
    }

    WorkEntryDto toDto(WorkEntry entry) {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        List<WorkSegment> segs = segments.findByWorkEntryIdOrderByStartedOnAsc(entry.getId());
        Long segmentStart = segments.findByOpenEntryId(entry.getId()).map(s -> s.getStartedOn().toEpochMilli()).orElse(null);
        String reviewStatus = entry.getEffectiveRevisionId() == null ? "DRAFT"
                : revisions.findById(entry.getEffectiveRevisionId()).map(WorkEntryRevision::getStatus).orElse("DRAFT");
        WorkEntryRevision revision = entry.getEffectiveRevisionId() == null ? null : revisions.findById(entry.getEffectiveRevisionId()).orElse(null);
        int live = entry.getRecordedSeconds();
        if ("RUNNING".equals(entry.getTimerState()) && segmentStart != null) {
            live += (int) Duration.between(Instant.ofEpochMilli(segmentStart), clock.instant()).toSeconds();
        }
        String startedOn = segs.isEmpty() ? null : segs.getFirst().getStartedOn().atZone(zone).toString();
        String endedOn = null;
        if (!segs.isEmpty() && segs.getLast().getEndedOn() != null) {
            endedOn = segs.getLast().getEndedOn().atZone(zone).toString();
        }
        String workDate = segs.isEmpty()
                ? entry.getCreatedOn().atZone(zone).toLocalDate().toString()
                : segs.getFirst().getStartedOn().atZone(zone).toLocalDate().toString();
        CandidateProfile candidate = candidates.findById(entry.getCandidateId()).orElse(null);
        ExpertProfile expert = experts.findById(entry.getExpertId()).orElse(null);
        return new WorkEntryDto(Ids.toJson(entry.getId()), entry.getDisplayCode(), Ids.toJson(entry.getEngagementId()),
                Ids.toJson(entry.getAssignmentId()), Ids.toJson(entry.getExpertId()), Ids.toJson(entry.getCandidateId()),
                candidate == null ? null : candidate.getFullName(),
                expert == null ? null : expert.getFullName(),
                candidate == null ? null : candidate.getWhatsappGroupName(),
                workDate, startedOn, endedOn, entry.getSource(), entry.getCategory(),
                entry.getTimerState(), live, clock.instant().toEpochMilli(), segmentStart, entry.getVersion(),
                entry.getSharedSummary(), reviewStatus,
                revision == null ? null : revision.getBillableSeconds(),
                revision == null ? null : revision.getPayableSeconds(),
                revision == null ? null : revision.getApprovedActualSeconds());
    }

    private String joinSummary(String issue, String workDone, String remarks) {
        StringBuilder sb = new StringBuilder();
        if (issue != null && !issue.isBlank()) {
            sb.append("Issue: ").append(issue.trim());
        }
        if (workDone != null && !workDone.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Work: ").append(workDone.trim());
        }
        if (remarks != null && !remarks.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Remarks: ").append(remarks.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}

@RestController
@RequestMapping("/api/v1/work-entries")
class WorkEntryController {
    private final TimeService timeService;
    WorkEntryController(TimeService timeService) { this.timeService = timeService; }

    @GetMapping
    public List<WorkEntryDto> list() { return timeService.listMine(); }

    @GetMapping("/history")
    public List<WorkEntryDto> history(@org.springframework.web.bind.annotation.RequestParam(required = false) String from,
                                      @org.springframework.web.bind.annotation.RequestParam(required = false) String to,
                                      @org.springframework.web.bind.annotation.RequestParam(required = false) String candidateId,
                                      @org.springframework.web.bind.annotation.RequestParam(required = false) String expertId,
                                      @org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
        return timeService.sessionHistory(from, to, candidateId, expertId, status);
    }

    @GetMapping("/active")
    public WorkEntryDto active() { return timeService.active(); }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkEntryDto start(@RequestBody TimerCommand command, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return timeService.start(command);
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkEntryDto manual(@RequestBody ManualEntryRequest request) { return timeService.createManual(request); }

    @GetMapping("/{id}")
    public WorkEntryDto get(@PathVariable String id) { return timeService.get(id); }

    @PostMapping("/{id}/pause")
    public WorkEntryDto pause(@PathVariable String id, @RequestBody(required = false) TimerCommand command) {
        return timeService.pause(id, command);
    }

    @PostMapping("/{id}/resume")
    public WorkEntryDto resume(@PathVariable String id) { return timeService.resume(id); }

    @PostMapping("/{id}/stop")
    public WorkEntryDto stop(@PathVariable String id, @RequestBody(required = false) StopCommand command) {
        return timeService.stop(id, command);
    }

    @PostMapping("/{id}/force-stop")
    public WorkEntryDto forceStop(@PathVariable String id, @RequestBody ReviewCommand command) {
        return timeService.forceStop(id, command.note());
    }

    @PostMapping("/{id}/submit")
    public WorkEntryDto submit(@PathVariable String id, @RequestBody ReviewCommand command) {
        return timeService.submit(id, command.note());
    }

    @PostMapping("/{id}/reviews/confirm")
    public WorkEntryDto confirm(@PathVariable String id, @RequestBody(required = false) ReviewCommand command) {
        return timeService.candidateConfirm(id, command);
    }

    @PostMapping("/{id}/reviews/dispute")
    public WorkEntryDto dispute(@PathVariable String id, @RequestBody ReviewCommand command) {
        return timeService.candidateDispute(id, command);
    }

    @PostMapping("/{id}/reviews/approve")
    public WorkEntryDto approve(@PathVariable String id, @RequestBody(required = false) ReviewCommand command) {
        return timeService.approve(id, command);
    }
}
