package com.dsms.operations;

import static com.dsms.operations.DailySheetDtos.DailyMarkerRequest;
import static com.dsms.operations.DailySheetDtos.DailySheetResponse;
import static com.dsms.operations.DailySheetDtos.DailySheetRow;
import static com.dsms.operations.DailySheetDtos.DailySheetSummary;

import com.dsms.audit.AuditService;
import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.candidates.PeopleService;
import com.dsms.candidates.SkillRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.ExpertAssignmentRepositoryBridge;
import com.dsms.engagements.SupportEngagement;
import com.dsms.engagements.SupportEngagementQuery;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import com.dsms.timetracking.WorkSegment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Repository
interface WorkSegmentQuery extends JpaRepository<WorkSegment, Long> {
    List<WorkSegment> findByWorkEntryIdOrderByStartedOnAsc(Long workEntryId);
}

@Service
public class DailySheetService {
    private static final Set<String> MARKER_STATUSES = Set.of(
            "PENDING", "RUNNING", "COMPLETED", "NO_SUPPORT_REQUIRED",
            "CANDIDATE_UNAVAILABLE", "SUPPORTER_UNAVAILABLE", "MISSED");

    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final SkillRepository skills;
    private final SupportEngagementQuery engagements;
    private final ExpertAssignmentRepositoryBridge assignments;
    private final WorkEntryRepository workEntries;
    private final WorkSegmentQuery segments;
    private final DailySupportMarkerRepository markers;
    private final PeopleService people;
    private final CurrentUser currentUser;
    private final DsmsProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    public DailySheetService(CandidateProfileRepository candidates, ExpertProfileRepository experts, SkillRepository skills,
                             SupportEngagementQuery engagements, ExpertAssignmentRepositoryBridge assignments,
                             WorkEntryRepository workEntries, WorkSegmentQuery segments, DailySupportMarkerRepository markers,
                             PeopleService people, CurrentUser currentUser, DsmsProperties properties,
                             AuditService auditService, Clock clock) {
        this.candidates = candidates;
        this.experts = experts;
        this.skills = skills;
        this.engagements = engagements;
        this.assignments = assignments;
        this.workEntries = workEntries;
        this.segments = segments;
        this.markers = markers;
        this.people = people;
        this.currentUser = currentUser;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailySheetResponse sheet(String dateText) {
        requireStaffOrExpert();
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate date = dateText == null || dateText.isBlank() ? LocalDate.now(clock.withZone(zone)) : LocalDate.parse(dateText);
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<CandidateProfile> activeCandidates = candidates.findAll().stream()
                .filter(c -> "ACTIVE".equals(c.getStatus()))
                .filter(people::canViewCandidate)
                .sorted(Comparator.comparing(CandidateProfile::getFullName))
                .toList();

        Map<Long, String> skillNames = new HashMap<>();
        skills.findAll().forEach(s -> skillNames.put(s.getId(), s.getName()));

        List<DailySheetRow> rows = new ArrayList<>();
        int supported = 0;
        int running = 0;
        int pending = 0;
        int missed = 0;
        int noSupport = 0;
        int totalSeconds = 0;

        for (CandidateProfile candidate : activeCandidates) {
            SupportEngagement engagement = activeEngagement(candidate.getId()).orElse(null);
            ExpertProfile supporter = engagement == null ? null : coveringExpert(engagement.getId(), dayStart.plusSeconds(1));
            if (currentUser.isExpert()) {
                ExpertProfile own = experts.findByUserId(currentUser.requireUserId()).orElse(null);
                if (own == null || supporter == null || !own.getId().equals(supporter.getId())) {
                    continue;
                }
            }

            List<WorkEntry> dayEntries = workEntries.findByCandidateIdOrderByCreatedOnDesc(candidate.getId()).stream()
                    .filter(e -> entryTouchesDay(e, dayStart, dayEnd))
                    .toList();

            WorkEntry runningEntry = dayEntries.stream().filter(e -> "RUNNING".equals(e.getTimerState()) || "PAUSED".equals(e.getTimerState())).findFirst().orElse(null);
            WorkEntry completed = dayEntries.stream().filter(e -> e.getTimerState() == null || "STOPPED".equals(e.getTimerState())).findFirst().orElse(null);

            Optional<DailySupportMarker> marker = markers.findByCandidateIdAndSupportDate(candidate.getId(), date);
            String dayStatus;
            if (marker.isPresent()) {
                dayStatus = marker.get().getDayStatus();
            } else if (runningEntry != null) {
                dayStatus = "RUNNING";
            } else if (completed != null && completed.getRecordedSeconds() > 0) {
                dayStatus = "COMPLETED";
            } else if (date.isBefore(LocalDate.now(clock.withZone(zone)))) {
                dayStatus = "MISSED";
            } else {
                dayStatus = "PENDING";
            }

            String start = null;
            String end = null;
            int seconds = 0;
            String workEntryId = null;
            WorkEntry focus = runningEntry != null ? runningEntry : completed;
            if (focus != null) {
                workEntryId = Ids.toJson(focus.getId());
                List<WorkSegment> segs = segments.findByWorkEntryIdOrderByStartedOnAsc(focus.getId());
                if (!segs.isEmpty()) {
                    start = segs.getFirst().getStartedOn().atZone(zone).toLocalTime().toString();
                    WorkSegment last = segs.getLast();
                    if (last.getEndedOn() != null) {
                        end = last.getEndedOn().atZone(zone).toLocalTime().toString();
                    }
                }
                seconds = focus.getRecordedSeconds();
            }

            switch (dayStatus) {
                case "COMPLETED" -> supported++;
                case "RUNNING" -> running++;
                case "PENDING" -> pending++;
                case "MISSED" -> missed++;
                case "NO_SUPPORT_REQUIRED", "CANDIDATE_UNAVAILABLE", "SUPPORTER_UNAVAILABLE" -> noSupport++;
                default -> {
                }
            }
            totalSeconds += seconds;

            String technology = candidate.getPrimarySkillId() == null ? null : skillNames.get(candidate.getPrimarySkillId());
            if (technology == null && candidate.getSkills() != null && !candidate.getSkills().isEmpty()) {
                technology = candidate.getSkills().iterator().next().getName();
            }
            rows.add(new DailySheetRow(
                    Ids.toJson(candidate.getId()),
                    candidate.getFullName(),
                    candidate.getWhatsappGroupName(),
                    technology,
                    supporter == null ? null : Ids.toJson(supporter.getId()),
                    supporter == null ? null : supporter.getFullName(),
                    engagement == null ? null : Ids.toJson(engagement.getId()),
                    start,
                    end,
                    formatHours(seconds),
                    dayStatus,
                    candidate.getWhatsappGroupLink(),
                    engagement == null ? null : engagement.getDefaultMeetingUrl(),
                    workEntryId,
                    marker.map(DailySupportMarker::getNotes).orElse(null)
            ));
        }

        int visibleActive = rows.size();
        DailySheetSummary summary = new DailySheetSummary(
                visibleActive, supported, running, pending, missed, noSupport, formatHours(totalSeconds));
        return new DailySheetResponse(date.toString(), summary, rows);
    }

    @Transactional
    public DailySheetRow setMarker(String candidateId, String dateText, DailyMarkerRequest request) {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Staff access required");
        }
        if (request.dayStatus() == null || !MARKER_STATUSES.contains(request.dayStatus())) {
            throw Exceptions.badRequest("INVALID_STATUS", "Unsupported daily status");
        }
        LocalDate date = LocalDate.parse(dateText);
        CandidateProfile candidate = people.requireCandidateEntity(Ids.parse(candidateId));
        DailySupportMarker marker = markers.findByCandidateIdAndSupportDate(candidate.getId(), date).orElseGet(DailySupportMarker::new);
        marker.setCandidateId(candidate.getId());
        marker.setSupportDate(date);
        marker.setDayStatus(request.dayStatus());
        marker.setNotes(request.notes());
        markers.save(marker);
        auditService.record("DAILY_MARKER", "daily_support_marker", Ids.toJson(marker.getId()), null,
                Map.of("candidateId", candidateId, "date", dateText, "status", request.dayStatus()));
        return sheet(dateText).rows().stream()
                .filter(r -> r.candidateId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> Exceptions.notFound("Candidate not on daily sheet"));
    }

    private Optional<SupportEngagement> activeEngagement(Long candidateId) {
        return engagements.findAll().stream()
                .filter(e -> e.getCandidateId().equals(candidateId))
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .findFirst();
    }

    private ExpertProfile coveringExpert(Long engagementId, Instant when) {
        return assignments.findAll().stream()
                .filter(a -> a.getEngagementId().equals(engagementId))
                .filter(a -> "ACCEPTED".equals(a.getAcceptanceState()))
                .filter(a -> !when.isBefore(a.getEffectiveStart()))
                .filter(a -> a.getEffectiveEnd() == null || when.isBefore(a.getEffectiveEnd()))
                .findFirst()
                .flatMap(a -> experts.findById(a.getExpertId()))
                .orElse(null);
    }

    private boolean entryTouchesDay(WorkEntry entry, Instant dayStart, Instant dayEnd) {
        List<WorkSegment> segs = segments.findByWorkEntryIdOrderByStartedOnAsc(entry.getId());
        if (segs.isEmpty()) {
            return !entry.getCreatedOn().isBefore(dayStart) && entry.getCreatedOn().isBefore(dayEnd);
        }
        return segs.stream().anyMatch(s -> {
            Instant end = s.getEndedOn() == null ? clock.instant() : s.getEndedOn();
            return s.getStartedOn().isBefore(dayEnd) && end.isAfter(dayStart);
        });
    }

    private void requireStaffOrExpert() {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator() && !currentUser.isExpert()) {
            throw Exceptions.forbidden("Access denied");
        }
    }

    private String formatHours(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h + "h " + String.format("%02dm", m);
    }
}

@RestController
@RequestMapping("/api/v1/daily-sheet")
class DailySheetController {
    private final DailySheetService service;
    DailySheetController(DailySheetService service) { this.service = service; }

    @GetMapping
    public DailySheetResponse get(@RequestParam(required = false) String date) {
        return service.sheet(date);
    }

    @PutMapping("/{candidateId}")
    public DailySheetRow marker(@PathVariable String candidateId,
                                @RequestParam String date,
                                @RequestBody DailyMarkerRequest request) {
        return service.setMarker(candidateId, date, request);
    }
}
