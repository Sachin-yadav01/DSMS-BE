package com.dsms.scheduling;

import com.dsms.audit.AuditService;
import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.api.PageResponse;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.EngagementService;
import com.dsms.engagements.ExpertAssignment;
import com.dsms.engagements.SupportEngagement;
import com.dsms.notifications.NotificationService;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Repository
interface SupportSessionRepository extends JpaRepository<SupportSession, Long> {
    List<SupportSession> findByEngagementIdOrderByScheduledStartAsc(Long engagementId);
    List<SupportSession> findByCandidateIdAndScheduledStartLessThanAndScheduledEndGreaterThanAndStatusIn(
            Long candidateId, Instant end, Instant start, List<String> statuses);
    List<SupportSession> findByExpertIdAndScheduledStartLessThanAndScheduledEndGreaterThanAndStatusIn(
            Long expertId, Instant end, Instant start, List<String> statuses);
    boolean existsBySeriesIdAndOccurrenceKey(Long seriesId, String occurrenceKey);
}

record SessionDto(String id, String displayCode, String engagementId, String assignmentId, String candidateId, String expertId,
                         String scheduledStart, String scheduledEnd, String sourceTimezone, String status, String topic,
                         String meetingPlatform, String meetingUrl) {}
record SessionRequest(String engagementId, String scheduledStart, String scheduledEnd, String sourceTimezone,
                             String topic, String meetingPlatform, String meetingUrl) {}
record RecurrenceRequest(String engagementId, int dayOfWeek, String startLocal, String endLocal, String timezone, int days) {}
record ChangeRequestDto(String type, String reason, String proposedStart, String proposedEnd) {}

@Service
public class SessionService {
    private static final List<String> BUSY = List.of("SCHEDULED", "IN_PROGRESS");
    private final SupportSessionRepository sessions;
    private final EngagementService engagements;
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final DisplayCodeService codes;
    private final CurrentUser currentUser;
    private final EntityManager entityManager;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final Clock clock;

    public SessionService(SupportSessionRepository sessions, EngagementService engagements, CandidateProfileRepository candidates,
                          ExpertProfileRepository experts, DisplayCodeService codes, CurrentUser currentUser,
                          EntityManager entityManager, NotificationService notifications, AuditService auditService, Clock clock) {
        this.sessions = sessions;
        this.engagements = engagements;
        this.candidates = candidates;
        this.experts = experts;
        this.codes = codes;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
        this.notifications = notifications;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SessionDto create(SessionRequest request) {
        Instant start = Instant.parse(request.scheduledStart());
        Instant end = Instant.parse(request.scheduledEnd());
        if (!end.isAfter(start)) {
            throw Exceptions.badRequest("INVALID_RANGE", "Session end must be after start");
        }
        if (start.isBefore(clock.instant().minusSeconds(60))) {
            throw Exceptions.badRequest("BACKDATED", "New sessions cannot be backdated");
        }
        validateMeetingUrl(request.meetingUrl());
        SupportEngagement engagement = engagements.lock(Ids.parse(request.engagementId()));
        if (!engagements.canView(engagement) || (!currentUser.isAdmin() && !currentUser.isCoordinator())) {
            throw Exceptions.forbidden("Cannot schedule this engagement");
        }
        if ("PAUSED".equals(engagement.getStatus()) || "COMPLETED".equals(engagement.getStatus()) || "CANCELLED".equals(engagement.getStatus())) {
            throw Exceptions.conflict("ENGAGEMENT_INACTIVE", "Cannot schedule on a paused or closed engagement");
        }
        ExpertAssignment assignment = engagements.requireAcceptedCovering(engagement.getId(), start);
        if (!EngagementService.covers(assignment, end.minusMillis(1))) {
            throw Exceptions.conflict("OUTSIDE_ASSIGNMENT", "Session must fit inside accepted assignment coverage");
        }
        CandidateProfile candidate = candidates.findById(engagement.getCandidateId()).orElseThrow();
        ExpertProfile expert = experts.findById(assignment.getExpertId()).orElseThrow();
        lockParticipants(candidate.getId(), expert.getId());
        assertNoBusyOverlap(candidate.getId(), expert.getId(), start, end, null);
        SupportSession session = new SupportSession();
        session.setDisplayCode(codes.next("SES"));
        session.setEngagementId(engagement.getId());
        session.setAssignmentId(assignment.getId());
        session.setCandidateId(candidate.getId());
        session.setExpertId(expert.getId());
        session.setCandidateNameSnapshot(candidate.getFullName());
        session.setExpertNameSnapshot(expert.getFullName());
        session.setScheduledStart(start);
        session.setScheduledEnd(end);
        session.setSourceTimezone(request.sourceTimezone() == null ? engagement.getScheduleTimezone() : request.sourceTimezone());
        session.setTopic(request.topic());
        session.setMeetingPlatform(request.meetingPlatform() == null ? engagement.getMeetingPlatform() : request.meetingPlatform());
        session.setMeetingUrl(request.meetingUrl() == null ? engagement.getDefaultMeetingUrl() : request.meetingUrl());
        session.setStatus("SCHEDULED");
        sessions.save(session);
        return toDto(session);
    }

    @Transactional
    public List<SessionDto> createSeries(RecurrenceRequest request) {
        SupportEngagement engagement = engagements.requireVisible(Ids.parse(request.engagementId()));
        ZoneId zone = ZoneId.of(request.timezone());
        LocalDate cursor = LocalDate.now(clock).with(DayOfWeek.of(request.dayOfWeek()));
        if (cursor.isBefore(LocalDate.now(clock))) {
            cursor = cursor.plusWeeks(1);
        }
        int horizon = request.days() <= 0 ? 30 : Math.min(request.days(), 90);
        LocalDate last = LocalDate.now(clock).plusDays(horizon);
        List<SessionDto> created = new ArrayList<>();
        while (!cursor.isAfter(last)) {
            ZonedDateTime startLocal = ZonedDateTime.of(cursor, LocalTime.parse(request.startLocal()), zone);
            ZonedDateTime endLocal = ZonedDateTime.of(cursor, LocalTime.parse(request.endLocal()), zone);
            SessionRequest single = new SessionRequest(Ids.toJson(engagement.getId()), startLocal.toInstant().toString(),
                    endLocal.toInstant().toString(), request.timezone(), "Recurring session", engagement.getMeetingPlatform(),
                    engagement.getDefaultMeetingUrl());
            try {
                created.add(create(single));
            } catch (RuntimeException ex) {
                // skip conflicting generated occurrences after preview-style best effort
            }
            cursor = cursor.plusWeeks(1);
        }
        return created;
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionDto> list(String engagementId, int page, int size) {
        List<SupportSession> all = engagementId == null
                ? sessions.findAll()
                : sessions.findByEngagementIdOrderByScheduledStartAsc(Ids.parse(engagementId));
        List<SessionDto> visible = all.stream()
                .filter(s -> engagements.canView(engagements.requireVisible(s.getEngagementId())))
                .map(this::toDto)
                .toList();
        int from = Math.min(page * size, visible.size());
        int to = Math.min(from + Math.min(Math.max(size, 1), 100), visible.size());
        return PageResponse.of(visible.subList(from, to), visible.size(), page, size);
    }

    @Transactional(readOnly = true)
    public SessionDto get(String id) {
        return toDto(requireVisibleSession(Ids.parse(id)));
    }

    @Transactional(readOnly = true)
    public SessionDto joinLink(String id) {
        SessionDto dto = get(id);
        if (dto.meetingUrl() == null) {
            throw Exceptions.notFound("No meeting link saved");
        }
        return dto;
    }

    @Transactional
    public SessionDto updateStatus(String id, String status, String reason) {
        SupportSession session = requireVisibleSession(Ids.parse(id));
        session.setStatus(status);
        session.setCancellationReason(reason);
        auditService.record("SESSION_STATUS", "support_session", id, null, java.util.Map.of("status", status));
        return toDto(session);
    }

    @Transactional
    public void requestChange(String id, ChangeRequestDto request) {
        SupportSession session = requireVisibleSession(Ids.parse(id));
        notifications.notifyUser(engagements.requireVisible(session.getEngagementId()).getCoordinatorUserId(),
                "Session change requested", request.reason(), "/sessions/" + id);
    }

    private SupportSession requireVisibleSession(Long id) {
        SupportSession session = sessions.findById(id).orElseThrow(() -> Exceptions.notFound("Session not found"));
        engagements.requireVisible(session.getEngagementId());
        return session;
    }

    private void lockParticipants(Long candidateId, Long expertId) {
        List<Long> ids = candidateId < expertId ? List.of(candidateId, expertId) : List.of(expertId, candidateId);
        for (Long id : ids) {
            entityManager.createNativeQuery("select id from candidate_profile where id = :id for update")
                    .setParameter("id", id).getResultList();
            entityManager.createNativeQuery("select id from expert_profile where id = :id for update")
                    .setParameter("id", id).getResultList();
        }
    }

    private void assertNoBusyOverlap(Long candidateId, Long expertId, Instant start, Instant end, Long ignoreId) {
        boolean candidateBusy = sessions.findByCandidateIdAndScheduledStartLessThanAndScheduledEndGreaterThanAndStatusIn(
                candidateId, end, start, BUSY).stream().anyMatch(s -> ignoreId == null || !ignoreId.equals(s.getId()));
        boolean expertBusy = sessions.findByExpertIdAndScheduledStartLessThanAndScheduledEndGreaterThanAndStatusIn(
                expertId, end, start, BUSY).stream().anyMatch(s -> ignoreId == null || !ignoreId.equals(s.getId()));
        if (candidateBusy || expertBusy) {
            throw Exceptions.conflict("SESSION_OVERLAP", "A conflicting session already exists");
        }
    }

    private void validateMeetingUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw Exceptions.badRequest("UNSAFE_URL", "Meeting links must use HTTPS");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!(host.endsWith("meet.google.com") || host.endsWith("zoom.us") || host.endsWith("zoom.com"))) {
            throw Exceptions.badRequest("UNSAFE_URL", "Only Google Meet or Zoom HTTPS links are allowed");
        }
    }

    private SessionDto toDto(SupportSession session) {
        return new SessionDto(Ids.toJson(session.getId()), session.getDisplayCode(), Ids.toJson(session.getEngagementId()),
                Ids.toJson(session.getAssignmentId()), Ids.toJson(session.getCandidateId()), Ids.toJson(session.getExpertId()),
                session.getScheduledStart().toString(), session.getScheduledEnd().toString(), session.getSourceTimezone(),
                session.getStatus(), session.getTopic(), session.getMeetingPlatform(), session.getMeetingUrl());
    }
}

@RestController
@RequestMapping("/api/v1/sessions")
class SessionController {
    private final SessionService sessions;
    SessionController(SessionService sessions) { this.sessions = sessions; }

    @GetMapping
    public PageResponse<SessionDto> list(@RequestParam(required = false) String engagementId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return sessions.list(engagementId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDto create(@RequestBody SessionRequest request) { return sessions.create(request); }

    @PostMapping("/recurring")
    public List<SessionDto> recurring(@RequestBody RecurrenceRequest request) { return sessions.createSeries(request); }

    @GetMapping("/{id}")
    public SessionDto get(@PathVariable String id) { return sessions.get(id); }

    @GetMapping("/{id}/join")
    public SessionDto join(@PathVariable String id) { return sessions.joinLink(id); }

    @PostMapping("/{id}/status")
    public SessionDto status(@PathVariable String id, @RequestBody java.util.Map<String, String> body) {
        return sessions.updateStatus(id, body.get("status"), body.get("reason"));
    }

    @PostMapping("/{id}/change-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void change(@PathVariable String id, @RequestBody ChangeRequestDto request) {
        sessions.requestChange(id, request);
    }
}
