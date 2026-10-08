package com.dsms.operations;

import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.candidates.PeopleService;
import com.dsms.candidates.SkillEntity;
import com.dsms.candidates.SkillRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.engagements.ExpertAssignment;
import com.dsms.engagements.ExpertAssignmentRepositoryBridge;
import com.dsms.engagements.SupportEngagement;
import com.dsms.engagements.SupportEngagementQuery;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

record CandidateProfileView(String id, String fullName, String technology, String groupName, String supportSince,
                            String primarySupporterName, String status, int supportDays, int sessions, String totalHours,
                            String averageDuration, List<Map<String, Object>> sessionsList, List<Map<String, Object>> assignments,
                            List<Map<String, Object>> payments, String notes) {}
record ExpertProfileView(String id, String fullName, String status, int activeCandidates, String todayHours, String monthHours,
                         int todaySessions, int monthSessions, List<Map<String, Object>> assignments, List<Map<String, Object>> recentSessions) {}
record TimesheetDayRow(String date, String supporterName, int sessionCount, String duration) {}
record CandidateTimesheet(String candidateId, String month, List<TimesheetDayRow> days, String monthlyTotal) {}
record TimesheetCandidateRow(String candidateName, int sessionCount, String duration) {}
record SupporterTimesheet(String expertId, String month, List<TimesheetCandidateRow> candidates, String monthlyTotal) {}

@Service
public class ProfileTimesheetService {
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final WorkEntryRepository workEntries;
    private final SupportEngagementQuery engagements;
    private final ExpertAssignmentRepositoryBridge assignments;
    private final CandidatePaymentRepository candidatePayments;
    private final SkillRepository skills;
    private final PeopleService people;
    private final DsmsProperties properties;
    private final Clock clock;

    public ProfileTimesheetService(CandidateProfileRepository candidates, ExpertProfileRepository experts,
                                   WorkEntryRepository workEntries, SupportEngagementQuery engagements,
                                   ExpertAssignmentRepositoryBridge assignments, CandidatePaymentRepository candidatePayments,
                                   SkillRepository skills, PeopleService people, DsmsProperties properties, Clock clock) {
        this.candidates = candidates;
        this.experts = experts;
        this.workEntries = workEntries;
        this.engagements = engagements;
        this.assignments = assignments;
        this.candidatePayments = candidatePayments;
        this.skills = skills;
        this.people = people;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CandidateProfileView candidateProfile(String id, String monthText) {
        CandidateProfile candidate = people.requireCandidateEntity(Ids.parse(id));
        if (!people.canViewCandidate(candidate)) {
            throw Exceptions.forbidden("Cannot view this candidate");
        }
        ZoneId zone = ZoneId.of(properties.getTimezone());
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.now(clock.withZone(zone)) : YearMonth.parse(monthText);
        List<WorkEntry> monthEntries = workEntries.findByCandidateIdOrderByCreatedOnDesc(candidate.getId()).stream()
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month))
                .toList();
        int seconds = monthEntries.stream().mapToInt(WorkEntry::getRecordedSeconds).sum();
        long days = monthEntries.stream().map(e -> e.getCreatedOn().atZone(zone).toLocalDate()).distinct().count();
        String avg = monthEntries.isEmpty() ? "0h 00m" : formatHours(seconds / monthEntries.size());
        String technology = candidate.getPrimarySkillId() == null ? null
                : skills.findById(candidate.getPrimarySkillId()).map(SkillEntity::getName).orElse(null);
        String supporter = resolvePrimarySupporterName(candidate.getId());
        List<Map<String, Object>> sessionMaps = monthEntries.stream().map(e -> Map.<String, Object>of(
                "id", Ids.toJson(e.getId()),
                "date", e.getCreatedOn().atZone(zone).toLocalDate().toString(),
                "seconds", e.getRecordedSeconds(),
                "summary", e.getSharedSummary() == null ? "" : e.getSharedSummary(),
                "status", e.getTimerState() == null ? "COMPLETED" : e.getTimerState()
        )).toList();
        List<Map<String, Object>> assignmentMaps = new ArrayList<>();
        for (SupportEngagement engagement : engagements.findAll()) {
            if (!engagement.getCandidateId().equals(candidate.getId())) {
                continue;
            }
            for (ExpertAssignment a : assignments.findAll()) {
                if (!a.getEngagementId().equals(engagement.getId())) {
                    continue;
                }
                String expertName = experts.findById(a.getExpertId()).map(ExpertProfile::getFullName).orElse("?");
                assignmentMaps.add(Map.of(
                        "id", Ids.toJson(a.getId()),
                        "expertName", expertName,
                        "state", a.getAcceptanceState(),
                        "start", a.getEffectiveStart().toString(),
                        "end", a.getEffectiveEnd() == null ? "" : a.getEffectiveEnd().toString()
                ));
            }
        }
        List<Map<String, Object>> payments = candidatePayments.findByCandidateIdOrderByBillingMonthDesc(candidate.getId()).stream()
                .map(p -> Map.<String, Object>of(
                        "id", Ids.toJson(p.getId()),
                        "month", p.getBillingMonth(),
                        "status", p.getStatus(),
                        "expected", p.getExpectedAmount().toPlainString(),
                        "paid", p.getPaidAmount().toPlainString()
                )).toList();
        return new CandidateProfileView(Ids.toJson(candidate.getId()), candidate.getFullName(),
                technology, candidate.getWhatsappGroupName(),
                candidate.getSupportStartDate() == null ? null : candidate.getSupportStartDate().toString(),
                supporter, candidate.getStatus(), (int) days, monthEntries.size(), formatHours(seconds), avg,
                sessionMaps, assignmentMaps, payments, candidate.getOperationalNotes());
    }

    @Transactional(readOnly = true)
    public ExpertProfileView expertProfile(String id) {
        ExpertProfile expert = people.requireExpertEntity(Ids.parse(id));
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        YearMonth month = YearMonth.from(today);
        List<WorkEntry> entries = workEntries.findByExpertIdOrderByCreatedOnDesc(expert.getId());
        List<WorkEntry> todayEntries = entries.stream().filter(e -> e.getCreatedOn().atZone(zone).toLocalDate().equals(today)).toList();
        List<WorkEntry> monthEntries = entries.stream().filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month)).toList();
        int active = (int) engagements.findAll().stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .filter(e -> assignments.findAll().stream().anyMatch(a ->
                        a.getEngagementId().equals(e.getId())
                                && a.getExpertId().equals(expert.getId())
                                && "ACCEPTED".equals(a.getAcceptanceState())
                                && a.getEffectiveEnd() == null))
                .count();
        List<Map<String, Object>> assignmentMaps = assignments.findAll().stream()
                .filter(a -> a.getExpertId().equals(expert.getId()))
                .sorted(Comparator.comparing(ExpertAssignment::getEffectiveStart).reversed())
                .limit(20)
                .map(a -> Map.<String, Object>of(
                        "id", Ids.toJson(a.getId()),
                        "engagementId", Ids.toJson(a.getEngagementId()),
                        "state", a.getAcceptanceState(),
                        "start", a.getEffectiveStart().toString()
                )).toList();
        List<Map<String, Object>> recent = entries.stream().limit(20).map(e -> Map.<String, Object>of(
                "id", Ids.toJson(e.getId()),
                "candidateId", Ids.toJson(e.getCandidateId()),
                "seconds", e.getRecordedSeconds(),
                "date", e.getCreatedOn().atZone(zone).toLocalDate().toString()
        )).toList();
        return new ExpertProfileView(Ids.toJson(expert.getId()), expert.getFullName(), expert.getStatus(), active,
                formatHours(todayEntries.stream().mapToInt(WorkEntry::getRecordedSeconds).sum()),
                formatHours(monthEntries.stream().mapToInt(WorkEntry::getRecordedSeconds).sum()),
                todayEntries.size(), monthEntries.size(), assignmentMaps, recent);
    }

    @Transactional(readOnly = true)
    public CandidateTimesheet candidateTimesheet(String id, String monthText) {
        CandidateProfile candidate = people.requireCandidateEntity(Ids.parse(id));
        if (!people.canViewCandidate(candidate)) {
            throw Exceptions.forbidden("Cannot view this candidate");
        }
        ZoneId zone = ZoneId.of(properties.getTimezone());
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.now(clock.withZone(zone)) : YearMonth.parse(monthText);
        Map<String, List<WorkEntry>> byDay = workEntries.findByCandidateIdOrderByCreatedOnDesc(candidate.getId()).stream()
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month))
                .collect(Collectors.groupingBy(e -> e.getCreatedOn().atZone(zone).toLocalDate().toString(), LinkedHashMap::new, Collectors.toList()));
        List<TimesheetDayRow> days = new ArrayList<>();
        int total = 0;
        for (var entry : byDay.entrySet()) {
            int seconds = entry.getValue().stream().mapToInt(WorkEntry::getRecordedSeconds).sum();
            total += seconds;
            String supporter = entry.getValue().stream()
                    .map(e -> experts.findById(e.getExpertId()).map(ExpertProfile::getFullName).orElse("?"))
                    .distinct()
                    .collect(Collectors.joining(", "));
            days.add(new TimesheetDayRow(entry.getKey(), supporter, entry.getValue().size(), formatHours(seconds)));
        }
        return new CandidateTimesheet(Ids.toJson(candidate.getId()), month.toString(), days, formatHours(total));
    }

    @Transactional(readOnly = true)
    public SupporterTimesheet supporterTimesheet(String id, String monthText) {
        ExpertProfile expert = people.requireExpertEntity(Ids.parse(id));
        ZoneId zone = ZoneId.of(properties.getTimezone());
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.now(clock.withZone(zone)) : YearMonth.parse(monthText);
        Map<Long, List<WorkEntry>> byCandidate = workEntries.findByExpertIdOrderByCreatedOnDesc(expert.getId()).stream()
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month))
                .collect(Collectors.groupingBy(WorkEntry::getCandidateId));
        List<TimesheetCandidateRow> rows = new ArrayList<>();
        int total = 0;
        for (var entry : byCandidate.entrySet()) {
            int seconds = entry.getValue().stream().mapToInt(WorkEntry::getRecordedSeconds).sum();
            total += seconds;
            String name = candidates.findById(entry.getKey()).map(CandidateProfile::getFullName).orElse("?");
            rows.add(new TimesheetCandidateRow(name, entry.getValue().size(), formatHours(seconds)));
        }
        return new SupporterTimesheet(Ids.toJson(expert.getId()), month.toString(), rows, formatHours(total));
    }

    private String resolvePrimarySupporterName(Long candidateId) {
        Instant now = clock.instant();
        for (SupportEngagement engagement : engagements.findAll()) {
            if (!engagement.getCandidateId().equals(candidateId) || !"ACTIVE".equals(engagement.getStatus())) {
                continue;
            }
            for (ExpertAssignment assignment : assignments.findAll()) {
                if (!assignment.getEngagementId().equals(engagement.getId()) || !"ACCEPTED".equals(assignment.getAcceptanceState())) {
                    continue;
                }
                if (now.isBefore(assignment.getEffectiveStart())) {
                    continue;
                }
                if (assignment.getEffectiveEnd() != null && !now.isBefore(assignment.getEffectiveEnd())) {
                    continue;
                }
                return experts.findById(assignment.getExpertId()).map(ExpertProfile::getFullName).orElse(null);
            }
        }
        return null;
    }

    private String formatHours(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h + "h " + String.format("%02dm", m);
    }
}

@RestController
@RequestMapping("/api/v1")
class ProfileTimesheetController {
    private final ProfileTimesheetService service;
    ProfileTimesheetController(ProfileTimesheetService service) { this.service = service; }

    @GetMapping("/candidates/{id}/profile")
    public CandidateProfileView candidateProfile(@PathVariable String id, @RequestParam(required = false) String month) {
        return service.candidateProfile(id, month);
    }

    @GetMapping("/experts/{id}/profile")
    public ExpertProfileView expertProfile(@PathVariable String id) {
        return service.expertProfile(id);
    }

    @GetMapping("/candidates/{id}/timesheet")
    public CandidateTimesheet candidateTimesheet(@PathVariable String id, @RequestParam(required = false) String month) {
        return service.candidateTimesheet(id, month);
    }

    @GetMapping("/experts/{id}/timesheet")
    public SupporterTimesheet expertTimesheet(@PathVariable String id, @RequestParam(required = false) String month) {
        return service.supporterTimesheet(id, month);
    }
}
