package com.dsms.operations;

import com.dsms.candidates.CandidateProfile;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.common.config.DsmsProperties;
import com.dsms.engagements.SupportEngagementQuery;
import com.dsms.notifications.NotificationService;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import com.dsms.users.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpsNotificationJob {
    private final CandidateProfileRepository candidates;
    private final WorkEntryRepository workEntries;
    private final CandidatePaymentRepository candidatePayments;
    private final SupporterPaymentRepository supporterPayments;
    private final SupportEngagementQuery engagements;
    private final UserAccountRepository users;
    private final NotificationService notifications;
    private final DsmsProperties properties;
    private final Clock clock;
    private final Set<String> sent = new HashSet<>();

    public OpsNotificationJob(CandidateProfileRepository candidates, WorkEntryRepository workEntries,
                              CandidatePaymentRepository candidatePayments, SupporterPaymentRepository supporterPayments,
                              SupportEngagementQuery engagements, UserAccountRepository users,
                              NotificationService notifications, DsmsProperties properties, Clock clock) {
        this.candidates = candidates;
        this.workEntries = workEntries;
        this.candidatePayments = candidatePayments;
        this.supporterPayments = supporterPayments;
        this.engagements = engagements;
        this.users = users;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "300000")
    @Transactional
    public void scan() {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        String dayKey = today.toString();

        for (CandidateProfile candidate : candidates.findAll()) {
            if (!"ACTIVE".equals(candidate.getStatus())) {
                continue;
            }
            boolean supported = workEntries.findByCandidateIdOrderByCreatedOnDesc(candidate.getId()).stream()
                    .anyMatch(e -> e.getCreatedOn().atZone(zone).toLocalDate().equals(today));
            if (!supported) {
                notifyOnce("unsupported:" + candidate.getId() + ":" + dayKey, candidate.getCoordinatorUserId(),
                        "Candidate not supported today", candidate.getFullName() + " has no support session yet today.",
                        "/daily-sheet");
            }
            if (candidate.getSupportStartDate() != null && candidate.getSupportStartDate().equals(today)) {
                notifyOnce("starts:" + candidate.getId() + ":" + dayKey, candidate.getCoordinatorUserId(),
                        "Candidate support starts today", candidate.getFullName() + " support starts today.",
                        "/candidates/" + candidate.getId());
            }
        }

        for (WorkEntry entry : workEntries.findAll()) {
            if (!"RUNNING".equals(entry.getTimerState())) {
                continue;
            }
            if (entry.getRecordedSeconds() >= properties.getLongSessionHours() * 3600) {
                engagements.findById(entry.getEngagementId()).ifPresent(eng ->
                        notifyOnce("long:" + entry.getId(), eng.getCoordinatorUserId(),
                                "Session running unusually long",
                                "Work entry " + entry.getDisplayCode() + " exceeded the long-session threshold.",
                                "/work"));
            }
        }

        for (CandidatePayment payment : candidatePayments.findAll()) {
            if ("OVERDUE".equals(payment.getStatus())
                    || (payment.getDueDate() != null && payment.getDueDate().isBefore(today)
                    && payment.getPaidAmount().compareTo(payment.getExpectedAmount()) < 0)) {
                candidates.findById(payment.getCandidateId()).ifPresent(c ->
                        notifyOnce("pay:" + payment.getId(), c.getCoordinatorUserId(),
                                "Candidate payment overdue",
                                c.getFullName() + " payment for " + payment.getBillingMonth() + " is overdue.",
                                "/candidate-payments"));
            }
        }

        for (SupporterPayment payment : supporterPayments.findAll()) {
            if ("PENDING".equals(payment.getStatus()) || "PARTIAL".equals(payment.getStatus())) {
                users.findAll().stream().filter(u -> u.getRole().name().equals("ADMIN")).findFirst().ifPresent(admin ->
                        notifyOnce("payout:" + payment.getId(), admin.getId(),
                                "Supporter payout pending",
                                "Payout for month " + payment.getBillingMonth() + " is still pending.",
                                "/supporter-payments"));
            }
        }
    }

    private void notifyOnce(String key, Long userId, String title, String body, String link) {
        if (userId == null || sent.contains(key)) {
            return;
        }
        notifications.notifyUser(userId, title, body, link);
        sent.add(key);
        if (sent.size() > 5000) {
            sent.clear();
        }
    }
}
