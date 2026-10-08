package com.dsms.billing;

import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.engagements.EngagementTerms;
import com.dsms.engagements.SupportEngagement;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRevision;
import com.dsms.timetracking.WorkSegment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Repository
interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {
    java.util.Optional<BillingPeriod> findByEngagementIdAndPeriodStart(Long engagementId, Instant periodStart);
    List<BillingPeriod> findByEngagementIdOrderByPeriodStartAsc(Long engagementId);
}

@Repository
interface ApprovedTimeAllocationRepository extends JpaRepository<ApprovedTimeAllocation, Long> {
    List<ApprovedTimeAllocation> findByRevisionId(Long revisionId);
    List<ApprovedTimeAllocation> findByBillingPeriodId(Long billingPeriodId);
    List<ApprovedTimeAllocation> findByExpertId(Long expertId);
    List<ApprovedTimeAllocation> findByCandidateId(Long candidateId);
}

@Service
public class AllocationService {
    private final BillingPeriodRepository periods;
    private final ApprovedTimeAllocationRepository allocations;
    private final com.dsms.engagements.EngagementTermsRepository terms;
    private final DsmsProperties properties;
    private final Clock clock;

    public AllocationService(BillingPeriodRepository periods, ApprovedTimeAllocationRepository allocations,
                             com.dsms.engagements.EngagementTermsRepository terms, DsmsProperties properties, Clock clock) {
        this.periods = periods;
        this.allocations = allocations;
        this.terms = terms;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public List<ApprovedTimeAllocation> allocate(WorkEntry entry, WorkEntryRevision revision, List<WorkSegment> segments) {
        Map<Long, Integer> actualByPeriod = new HashMap<>();
        for (WorkSegment segment : segments) {
            if (segment.getEndedOn() == null) {
                continue;
            }
            Instant cursor = segment.getStartedOn();
            while (cursor.isBefore(segment.getEndedOn())) {
                BillingPeriod period = periodCovering(entry.getEngagementId(), cursor);
                Instant sliceEnd = segment.getEndedOn().isBefore(period.getPeriodEnd()) ? segment.getEndedOn() : period.getPeriodEnd();
                int seconds = (int) Duration.between(cursor, sliceEnd).toSeconds();
                actualByPeriod.merge(period.getId(), seconds, Integer::sum);
                cursor = sliceEnd;
            }
        }
        int totalActual = actualByPeriod.values().stream().mapToInt(Integer::intValue).sum();
        if (totalActual <= 0) {
            return List.of();
        }
        Map<Long, Integer> billable = largestRemainder(actualByPeriod, revision.getBillableSeconds() == null ? 0 : revision.getBillableSeconds(), totalActual);
        Map<Long, Integer> payable = largestRemainder(actualByPeriod, revision.getPayableSeconds() == null ? 0 : revision.getPayableSeconds(), totalActual);
        Map<Long, Integer> approved = largestRemainder(actualByPeriod, revision.getApprovedActualSeconds() == null ? totalActual : revision.getApprovedActualSeconds(), totalActual);
        List<ApprovedTimeAllocation> saved = new ArrayList<>();
        for (Long periodId : actualByPeriod.keySet()) {
            ApprovedTimeAllocation allocation = new ApprovedTimeAllocation();
            allocation.setWorkEntryId(entry.getId());
            allocation.setRevisionId(revision.getId());
            allocation.setBillingPeriodId(periodId);
            allocation.setAssignmentId(entry.getAssignmentId());
            allocation.setExpertId(entry.getExpertId());
            allocation.setCandidateId(entry.getCandidateId());
            allocation.setApprovedActualSeconds(approved.getOrDefault(periodId, 0));
            allocation.setBillableSeconds(billable.getOrDefault(periodId, 0));
            allocation.setPayableSeconds(payable.getOrDefault(periodId, 0));
            allocation.setCreatedOn(clock.instant());
            saved.add(allocations.save(allocation));
        }
        return saved;
    }

    public BillingPeriod periodCovering(Long engagementId, Instant instant) {
        ZoneId zone = ZoneId.of(properties.getTimezone());
        YearMonth month = YearMonth.from(instant.atZone(zone));
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return periods.findByEngagementIdAndPeriodStart(engagementId, start).orElseGet(() -> createPeriod(engagementId, start, end, zone));
    }

    private BillingPeriod createPeriod(Long engagementId, Instant start, Instant end, ZoneId zone) {
        EngagementTerms latest = terms.findByEngagementIdOrderByEffectiveFromDesc(engagementId).stream()
                .filter(t -> "APPROVED".equals(t.getStatus()))
                .findFirst()
                .orElseThrow(() -> Exceptions.conflict("TERMS_REQUIRED", "Approved commercial terms are required before allocating time"));
        BillingPeriod period = new BillingPeriod();
        period.setEngagementId(engagementId);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        period.setBusinessTimezone(zone.getId());
        period.setTermsId(latest.getId());
        period.setPlanType(latest.getPlanType());
        period.setCurrency(properties.getCurrency());
        period.setHourlyRate(latest.getHourlyRate());
        period.setConfirmedBaseFee(latest.getMonthlyBaseFee());
        period.setConfirmedIncludedSeconds(latest.getIncludedSeconds());
        period.setOverageEnabled(latest.isOverageEnabled());
        period.setOverageHourlyRate(latest.getOverageHourlyRate());
        period.setPartialMonth(false);
        period.setTermsConfirmed(true);
        period.setStatus("OPEN");
        return periods.save(period);
    }

    static Map<Long, Integer> largestRemainder(Map<Long, Integer> weights, int total, int weightSum) {
        Map<Long, Integer> result = new HashMap<>();
        if (weightSum <= 0 || total <= 0) {
            weights.keySet().forEach(k -> result.put(k, 0));
            return result;
        }
        int assigned = 0;
        record Remainder(Long id, double fraction) {}
        List<Remainder> remainders = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : weights.entrySet()) {
            double exact = (double) total * entry.getValue() / weightSum;
            int base = (int) Math.floor(exact);
            result.put(entry.getKey(), base);
            assigned += base;
            remainders.add(new Remainder(entry.getKey(), exact - base));
        }
        remainders.sort(Comparator.comparingDouble(Remainder::fraction).reversed());
        int leftover = total - assigned;
        for (int i = 0; i < leftover && i < remainders.size(); i++) {
            result.merge(remainders.get(i).id(), 1, Integer::sum);
        }
        return result;
    }
}
