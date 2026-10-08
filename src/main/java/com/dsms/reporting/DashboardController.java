package com.dsms.reporting;

import com.dsms.billing.InvoiceRepository;
import com.dsms.billing.PayoutStatementRepository;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.SupportEngagement;
import com.dsms.engagements.SupportEngagementQuery;
import com.dsms.operations.DailySheetDtos.DailySheetSummary;
import com.dsms.operations.DailySheetService;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final SupportEngagementQuery engagements;
    private final WorkEntryRepository workEntries;
    private final InvoiceRepository invoices;
    private final PayoutStatementRepository payouts;
    private final CurrentUser currentUser;
    private final DailySheetService dailySheetService;
    private final DsmsProperties properties;
    private final Clock clock;

    public DashboardController(CandidateProfileRepository candidates, ExpertProfileRepository experts,
                               SupportEngagementQuery engagements, WorkEntryRepository workEntries,
                               InvoiceRepository invoices, PayoutStatementRepository payouts, CurrentUser currentUser,
                               DailySheetService dailySheetService, DsmsProperties properties, Clock clock) {
        this.candidates = candidates;
        this.experts = experts;
        this.engagements = engagements;
        this.workEntries = workEntries;
        this.invoices = invoices;
        this.payouts = payouts;
        this.currentUser = currentUser;
        this.dailySheetService = dailySheetService;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/dashboards/me")
    public Map<String, Object> me() {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        YearMonth month = YearMonth.from(today);
        var sheet = dailySheetService.sheet(today.toString());
        DailySheetSummary summary = sheet.summary();
        long totalCandidates = candidates.count();
        long activeSupport = candidates.findAll().stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        long supporters = experts.findAll().stream().filter(e -> !"INACTIVE".equals(e.getStatus())).count();
        long runningNow = workEntries.findAll().stream().filter(e -> "RUNNING".equals(e.getTimerState())).count();
        int todaySeconds = workEntries.findAll().stream()
                .filter(e -> e.getCreatedOn().atZone(zone).toLocalDate().equals(today))
                .mapToInt(WorkEntry::getRecordedSeconds).sum();
        int monthSeconds = workEntries.findAll().stream()
                .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(month))
                .mapToInt(WorkEntry::getRecordedSeconds).sum();
        var invoiceTotals = invoices.findAll();
        var payoutTotals = payouts.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", currentUser.requirePrincipal().getRole().name());
        result.put("totalCandidates", totalCandidates);
        result.put("activeCandidates", activeSupport);
        result.put("activeSupport", activeSupport);
        result.put("activeExperts", supporters);
        result.put("supporters", supporters);
        result.put("activeEngagements", engagements.findAll().stream().filter(e -> "ACTIVE".equals(e.getStatus())).count());
        result.put("supportedToday", summary.supported());
        result.put("pendingToday", summary.pending());
        result.put("runningNow", runningNow);
        result.put("todaysHours", formatHours(todaySeconds));
        result.put("thisMonthHours", formatHours(monthSeconds));
        result.put("pendingReviews", workEntries.findAll().stream().filter(e -> e.getEffectiveRevisionId() != null).count());
        result.put("runningSessions", sheet.rows().stream().filter(r -> "RUNNING".equals(r.dayStatus())).toList());
        result.put("pendingCandidates", sheet.rows().stream().filter(r -> "PENDING".equals(r.dayStatus()) || "MISSED".equals(r.dayStatus())).toList());
        result.put("invoicedAmount", invoiceTotals.stream().filter(i -> "ISSUED".equals(i.getStatus())).map(i -> i.getTotal()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
        result.put("collectedAmount", invoiceTotals.stream().filter(i -> !"VOID".equals(i.getStatus())).map(i -> i.getCollectedAmount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
        result.put("expertEarnings", payoutTotals.stream().filter(p -> "ISSUED".equals(p.getStatus())).map(p -> p.getTotal()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
        result.put("unpaidPayouts", payoutTotals.stream().filter(p -> "ISSUED".equals(p.getStatus())).map(p -> p.getTotal().subtract(p.getPaidAmount())).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
        return result;
    }

    @GetMapping("/reports/time")
    public List<Map<String, Object>> timeReport() {
        return workEntries.findAll().stream()
                .map(e -> Map.<String, Object>of(
                        "id", Ids.toJson(e.getId()),
                        "engagementId", Ids.toJson(e.getEngagementId()),
                        "candidateId", Ids.toJson(e.getCandidateId()),
                        "expertId", Ids.toJson(e.getExpertId()),
                        "recordedSeconds", e.getRecordedSeconds(),
                        "source", e.getSource(),
                        "category", e.getCategory()))
                .toList();
    }

    @GetMapping("/reports/{type}")
    public List<Map<String, Object>> report(@org.springframework.web.bind.annotation.PathVariable String type,
                                            @RequestParam(required = false) String date,
                                            @RequestParam(required = false) String month) {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate day = date == null || date.isBlank() ? LocalDate.now(clock.withZone(zone)) : LocalDate.parse(date);
        YearMonth ym = month == null || month.isBlank() ? YearMonth.from(day) : YearMonth.parse(month);
        return switch (type) {
            case "daily-support" -> dailySheetService.sheet(day.toString()).rows().stream()
                    .map(r -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("candidateId", r.candidateId());
                        row.put("candidate", r.candidateName());
                        row.put("group", nullToEmpty(r.groupName()));
                        row.put("technology", nullToEmpty(r.technology()));
                        row.put("supporterId", nullToEmpty(r.supporterId()));
                        row.put("supporter", nullToEmpty(r.supporterName()));
                        row.put("status", r.dayStatus());
                        row.put("duration", r.duration());
                        row.put("start", nullToEmpty(r.start()));
                        row.put("end", nullToEmpty(r.end()));
                        row.put("whatsappGroupLink", nullToEmpty(r.whatsappGroupLink()));
                        row.put("meetingUrl", nullToEmpty(r.meetingUrl()));
                        return row;
                    })
                    .toList();
            case "candidate-summary" -> {
                YearMonth summaryMonth = ym;
                yield candidates.findAll().stream()
                        .filter(c -> "ACTIVE".equals(c.getStatus()) || "NOT_STARTED".equals(c.getStatus()))
                        .map(c -> {
                            int seconds = workEntries.findByCandidateIdOrderByCreatedOnDesc(c.getId()).stream()
                                    .filter(w -> YearMonth.from(w.getCreatedOn().atZone(zone)).equals(summaryMonth))
                                    .mapToInt(WorkEntry::getRecordedSeconds).sum();
                            long sessions = workEntries.findByCandidateIdOrderByCreatedOnDesc(c.getId()).stream()
                                    .filter(w -> YearMonth.from(w.getCreatedOn().atZone(zone)).equals(summaryMonth))
                                    .count();
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("candidateId", Ids.toJson(c.getId()));
                            row.put("candidate", c.getFullName());
                            row.put("group", nullToEmpty(c.getWhatsappGroupName()));
                            row.put("status", c.getStatus());
                            row.put("sessions", sessions);
                            row.put("duration", formatHours(seconds));
                            return row;
                        })
                        .toList();
            }
            case "monthly-support" -> workEntries.findAll().stream()
                    .filter(e -> YearMonth.from(e.getCreatedOn().atZone(zone)).equals(ym))
                    .collect(Collectors.groupingBy(WorkEntry::getCandidateId))
                    .entrySet().stream()
                    .map(e -> Map.<String, Object>of(
                            "candidateId", Ids.toJson(e.getKey()),
                            "sessions", e.getValue().size(),
                            "seconds", e.getValue().stream().mapToInt(WorkEntry::getRecordedSeconds).sum()))
                    .toList();
            case "candidate-hours" -> workEntries.findAll().stream()
                    .collect(Collectors.groupingBy(WorkEntry::getCandidateId))
                    .entrySet().stream()
                    .map(e -> Map.<String, Object>of(
                            "candidateId", Ids.toJson(e.getKey()),
                            "seconds", e.getValue().stream().mapToInt(WorkEntry::getRecordedSeconds).sum()))
                    .toList();
            case "supporter-hours" -> workEntries.findAll().stream()
                    .collect(Collectors.groupingBy(WorkEntry::getExpertId))
                    .entrySet().stream()
                    .map(e -> Map.<String, Object>of(
                            "expertId", Ids.toJson(e.getKey()),
                            "seconds", e.getValue().stream().mapToInt(WorkEntry::getRecordedSeconds).sum()))
                    .toList();
            case "missed-support" -> dailySheetService.sheet(day.toString()).rows().stream()
                    .filter(r -> "MISSED".equals(r.dayStatus()))
                    .map(r -> Map.<String, Object>of("candidate", r.candidateName(), "status", r.dayStatus()))
                    .toList();
            case "active-candidates" -> candidates.findAll().stream()
                    .filter(c -> "ACTIVE".equals(c.getStatus()))
                    .map(c -> Map.<String, Object>of(
                            "id", Ids.toJson(c.getId()),
                            "name", c.getFullName(),
                            "group", nullToEmpty(c.getWhatsappGroupName()),
                            "status", c.getStatus()))
                    .toList();
            case "supporter-workload" -> experts.findAll().stream()
                    .map(e -> Map.<String, Object>of(
                            "id", Ids.toJson(e.getId()),
                            "name", e.getFullName(),
                            "status", e.getStatus(),
                            "monthSeconds", workEntries.findByExpertIdOrderByCreatedOnDesc(e.getId()).stream()
                                    .filter(w -> YearMonth.from(w.getCreatedOn().atZone(zone)).equals(ym))
                                    .mapToInt(WorkEntry::getRecordedSeconds).sum()))
                    .toList();
            default -> List.of(Map.of("error", "Unknown report type"));
        };
    }

    @GetMapping(value = "/reports/{type}.csv", produces = "text/csv")
    public ResponseEntity<String> reportCsv(@org.springframework.web.bind.annotation.PathVariable String type,
                                            @RequestParam(required = false) String date,
                                            @RequestParam(required = false) String month) {
        List<Map<String, Object>> rows = report(type, date, month);
        StringBuilder sb = new StringBuilder();
        if (!rows.isEmpty()) {
            List<String> headers = new ArrayList<>(rows.getFirst().keySet());
            sb.append(String.join(",", headers)).append('\n');
            for (Map<String, Object> row : rows) {
                sb.append(headers.stream().map(h -> csv(row.get(h))).collect(Collectors.joining(","))).append('\n');
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + type + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    private String formatHours(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h + "h " + String.format("%02dm", m);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
