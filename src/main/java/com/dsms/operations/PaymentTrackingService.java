package com.dsms.operations;

import com.dsms.audit.AuditService;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfile;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.AuditedEntity;
import com.dsms.common.security.CurrentUser;
import com.dsms.timetracking.WorkEntry;
import com.dsms.timetracking.WorkEntryRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.dsms.candidates.CandidateProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Getter
@Setter
@Entity
@Table(name = "candidate_payment")
class CandidatePayment extends AuditedEntity {
    @Column(nullable = false) private Long candidateId;
    @Column(nullable = false) private String billingMonth;
    @Column(nullable = false) private BigDecimal expectedAmount;
    @Column(nullable = false) private BigDecimal paidAmount;
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private String method;
    private String externalReference;
    private String notes;
    @Column(nullable = false) private String status;
}

@Getter
@Setter
@Entity
@Table(name = "supporter_payment")
class SupporterPayment extends AuditedEntity {
    @Column(nullable = false) private Long expertId;
    @Column(nullable = false) private String billingMonth;
    @Column(nullable = false) private String paymentModel;
    @Column(nullable = false) private BigDecimal baseAmount;
    @Column(nullable = false) private BigDecimal adjustmentAmount;
    private String adjustmentReason;
    @Column(nullable = false) private BigDecimal finalPayable;
    @Column(nullable = false) private BigDecimal amountPaid;
    @Column(nullable = false) private String status;
    private String notes;
}

@Repository
interface CandidatePaymentRepository extends JpaRepository<CandidatePayment, Long> {
    List<CandidatePayment> findByCandidateIdOrderByBillingMonthDesc(Long candidateId);
    List<CandidatePayment> findAllByOrderByBillingMonthDesc();
}

@Repository
interface SupporterPaymentRepository extends JpaRepository<SupporterPayment, Long> {
    List<SupporterPayment> findByExpertIdOrderByBillingMonthDesc(Long expertId);
    List<SupporterPayment> findAllByOrderByBillingMonthDesc();
}

record CandidatePaymentDto(String id, String candidateId, String candidateName, String candidateDisplayCode, String groupName,
                           String billingMonth, String expectedAmount, String paidAmount, String pendingAmount,
                           String dueDate, String paymentDate, String method, String externalReference, String notes,
                           String status) {}
record SupporterPaymentDto(String id, String expertId, String expertName, String expertDisplayCode, String groupName,
                           String billingMonth, String paymentModel, String calculationLabel, String approvedHours,
                           String hourlyRate, String baseAmount, String adjustmentAmount, String adjustmentReason,
                           String finalPayable, String amountPaid, String balanceAmount, String status, String notes) {}
record CandidatePaymentRequest(String candidateId, String billingMonth, String expectedAmount, String paidAmount,
                               String dueDate, String paymentDate, String method, String externalReference, String notes,
                               String status) {}
record SupporterPaymentRequest(String expertId, String billingMonth, String paymentModel, String baseAmount,
                               String adjustmentAmount, String adjustmentReason, String amountPaid, String notes,
                               String status) {}

@Service
public class PaymentTrackingService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING", "PARTIAL", "PAID", "OVERDUE", "WAIVED");
    private static final Set<String> MODELS = Set.of("HOURLY", "FIXED_MONTHLY", "PER_CANDIDATE", "CUSTOM");

    private final CandidatePaymentRepository candidatePayments;
    private final SupporterPaymentRepository supporterPayments;
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final WorkEntryRepository workEntries;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public PaymentTrackingService(CandidatePaymentRepository candidatePayments, SupporterPaymentRepository supporterPayments,
                                  CandidateProfileRepository candidates, ExpertProfileRepository experts,
                                  WorkEntryRepository workEntries, CurrentUser currentUser, AuditService auditService) {
        this.candidatePayments = candidatePayments;
        this.supporterPayments = supporterPayments;
        this.candidates = candidates;
        this.experts = experts;
        this.workEntries = workEntries;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CandidatePaymentDto> listCandidatePayments() {
        requireStaff();
        return candidatePayments.findAllByOrderByBillingMonthDesc().stream().map(this::toCandidate).toList();
    }

    @Transactional
    public CandidatePaymentDto createCandidatePayment(CandidatePaymentRequest request) {
        requireStaff();
        candidates.findById(Ids.parse(request.candidateId())).orElseThrow(() -> Exceptions.notFound("Candidate not found"));
        CandidatePayment payment = new CandidatePayment();
        applyCandidate(payment, request, true);
        candidatePayments.save(payment);
        auditService.record("CANDIDATE_PAYMENT_CREATE", "candidate_payment", Ids.toJson(payment.getId()), null,
                Map.of("status", payment.getStatus(), "expected", payment.getExpectedAmount().toPlainString()));
        return toCandidate(payment);
    }

    @Transactional
    public CandidatePaymentDto updateCandidatePayment(String id, CandidatePaymentRequest request) {
        requireStaff();
        CandidatePayment payment = candidatePayments.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Payment not found"));
        Map<String, Object> before = Map.of("status", payment.getStatus(), "paid", payment.getPaidAmount().toPlainString());
        applyCandidate(payment, request, false);
        auditService.record("CANDIDATE_PAYMENT_UPDATE", "candidate_payment", id, before,
                Map.of("status", payment.getStatus(), "paid", payment.getPaidAmount().toPlainString()));
        return toCandidate(payment);
    }

    @Transactional(readOnly = true)
    public List<SupporterPaymentDto> listSupporterPayments() {
        requireStaffOrOwnExpert();
        if (currentUser.isExpert()) {
            ExpertProfile expert = experts.findByUserId(currentUser.requireUserId()).orElseThrow(() -> Exceptions.notFound("Expert not found"));
            return supporterPayments.findByExpertIdOrderByBillingMonthDesc(expert.getId()).stream().map(this::toSupporter).toList();
        }
        return supporterPayments.findAllByOrderByBillingMonthDesc().stream().map(this::toSupporter).toList();
    }

    @Transactional
    public SupporterPaymentDto createSupporterPayment(SupporterPaymentRequest request) {
        requireStaff();
        ExpertProfile expert = experts.findById(Ids.parse(request.expertId())).orElseThrow(() -> Exceptions.notFound("Expert not found"));
        SupporterPayment payment = new SupporterPayment();
        applySupporter(payment, request, expert, true);
        supporterPayments.save(payment);
        auditService.record("SUPPORTER_PAYMENT_CREATE", "supporter_payment", Ids.toJson(payment.getId()), null,
                Map.of("model", payment.getPaymentModel(), "final", payment.getFinalPayable().toPlainString()));
        return toSupporter(payment);
    }

    @Transactional
    public SupporterPaymentDto updateSupporterPayment(String id, SupporterPaymentRequest request) {
        requireStaff();
        SupporterPayment payment = supporterPayments.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Payment not found"));
        ExpertProfile expert = experts.findById(payment.getExpertId()).orElseThrow();
        Map<String, Object> before = Map.of("paid", payment.getAmountPaid().toPlainString(), "final", payment.getFinalPayable().toPlainString());
        applySupporter(payment, request, expert, false);
        auditService.record("SUPPORTER_PAYMENT_UPDATE", "supporter_payment", id, before,
                Map.of("paid", payment.getAmountPaid().toPlainString(), "final", payment.getFinalPayable().toPlainString(),
                        "adjustment", payment.getAdjustmentAmount().toPlainString()));
        return toSupporter(payment);
    }

    private void applyCandidate(CandidatePayment payment, CandidatePaymentRequest request, boolean creating) {
        if (creating) {
            payment.setCandidateId(Ids.parse(request.candidateId()));
            payment.setBillingMonth(request.billingMonth());
        }
        payment.setExpectedAmount(money(request.expectedAmount()));
        payment.setPaidAmount(request.paidAmount() == null ? BigDecimal.ZERO : money(request.paidAmount()));
        payment.setDueDate(blank(request.dueDate()) ? null : LocalDate.parse(request.dueDate()));
        payment.setPaymentDate(blank(request.paymentDate()) ? null : LocalDate.parse(request.paymentDate()));
        payment.setMethod(request.method());
        payment.setExternalReference(request.externalReference());
        payment.setNotes(request.notes());
        payment.setStatus(resolveCandidateStatus(request.status(), payment));
    }

    private void applySupporter(SupporterPayment payment, SupporterPaymentRequest request, ExpertProfile expert, boolean creating) {
        String model = request.paymentModel() == null ? (expert.getPaymentType() == null ? "HOURLY" : expert.getPaymentType()) : request.paymentModel();
        if (!MODELS.contains(model)) {
            throw Exceptions.badRequest("INVALID_MODEL", "Unsupported payment model");
        }
        if (creating) {
            payment.setExpertId(expert.getId());
            payment.setBillingMonth(request.billingMonth());
        }
        payment.setPaymentModel(model);
        BigDecimal base = blank(request.baseAmount()) ? computeBase(expert, model, payment.getBillingMonth()) : money(request.baseAmount());
        BigDecimal adjustment = blank(request.adjustmentAmount()) ? BigDecimal.ZERO : money(request.adjustmentAmount());
        payment.setBaseAmount(base);
        payment.setAdjustmentAmount(adjustment);
        payment.setAdjustmentReason(request.adjustmentReason());
        payment.setFinalPayable(base.add(adjustment).setScale(2, RoundingMode.HALF_UP));
        payment.setAmountPaid(blank(request.amountPaid()) ? BigDecimal.ZERO : money(request.amountPaid()));
        payment.setNotes(request.notes());
        if (creating && blank(request.status())) {
            payment.setStatus("DRAFT");
        } else {
            payment.setStatus(resolveSupporterStatus(request.status(), payment));
        }
    }

    private BigDecimal computeBase(ExpertProfile expert, String model, String billingMonth) {
        int seconds = approvedSeconds(expert.getId(), billingMonth);
        return switch (model) {
            case "HOURLY" -> expert.getHourlyPayoutRate()
                    .multiply(BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
            case "FIXED_MONTHLY", "PER_CANDIDATE", "CUSTOM" -> expert.getHourlyPayoutRate().setScale(2, RoundingMode.HALF_UP);
            default -> BigDecimal.ZERO;
        };
    }

    private int approvedSeconds(Long expertId, String billingMonth) {
        YearMonth month = YearMonth.parse(billingMonth);
        return workEntries.findByExpertIdOrderByCreatedOnDesc(expertId).stream()
                .filter(e -> e.getCreatedOn() != null && YearMonth.from(e.getCreatedOn().atZone(ZoneOffset.UTC)).equals(month))
                .mapToInt(WorkEntry::getRecordedSeconds)
                .sum();
    }

    private String resolveCandidateStatus(String requested, CandidatePayment payment) {
        if (requested != null && !requested.isBlank()) {
            if (!STATUSES.contains(requested)) {
                throw Exceptions.badRequest("INVALID_STATUS", "Unsupported payment status");
            }
            return requested;
        }
        if (payment.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            if (payment.getDueDate() != null && payment.getDueDate().isBefore(LocalDate.now())) {
                return "OVERDUE";
            }
            return "PENDING";
        }
        if (payment.getPaidAmount().compareTo(payment.getExpectedAmount()) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private String resolveSupporterStatus(String requested, SupporterPayment payment) {
        if (requested != null && !requested.isBlank()) {
            if (!STATUSES.contains(requested)) {
                throw Exceptions.badRequest("INVALID_STATUS", "Unsupported payment status");
            }
            return requested;
        }
        if ("DRAFT".equals(payment.getStatus()) && payment.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            return "DRAFT";
        }
        if (payment.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            return "PENDING";
        }
        if (payment.getAmountPaid().compareTo(payment.getFinalPayable()) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private CandidatePaymentDto toCandidate(CandidatePayment payment) {
        CandidateProfile candidate = candidates.findById(payment.getCandidateId()).orElse(null);
        String name = candidate == null ? "Unknown candidate" : candidate.getFullName();
        String code = candidate == null ? null : candidate.getDisplayCode();
        String group = candidate == null ? null : candidate.getWhatsappGroupName();
        BigDecimal pending = payment.getExpectedAmount().subtract(payment.getPaidAmount()).max(BigDecimal.ZERO);
        return new CandidatePaymentDto(Ids.toJson(payment.getId()), Ids.toJson(payment.getCandidateId()), name, code, group,
                payment.getBillingMonth(), payment.getExpectedAmount().toPlainString(), payment.getPaidAmount().toPlainString(),
                pending.toPlainString(), payment.getDueDate() == null ? null : payment.getDueDate().toString(),
                payment.getPaymentDate() == null ? null : payment.getPaymentDate().toString(),
                payment.getMethod(), payment.getExternalReference(), payment.getNotes(), payment.getStatus());
    }

    private SupporterPaymentDto toSupporter(SupporterPayment payment) {
        ExpertProfile expert = experts.findById(payment.getExpertId()).orElse(null);
        String name = expert == null ? "Unknown supporter" : expert.getFullName();
        String code = expert == null ? null : expert.getDisplayCode();
        String group = name + " Support";
        BigDecimal hourly = expert == null ? BigDecimal.ZERO : expert.getHourlyPayoutRate();
        String rate = hourly.setScale(2, RoundingMode.HALF_UP).toPlainString();
        BigDecimal hours = BigDecimal.valueOf(approvedSeconds(payment.getExpertId(), payment.getBillingMonth()))
                .divide(BigDecimal.valueOf(3600), 1, RoundingMode.HALF_UP);
        if ("HOURLY".equals(payment.getPaymentModel())
                && hours.compareTo(BigDecimal.ZERO) == 0
                && hourly.compareTo(BigDecimal.ZERO) > 0
                && payment.getBaseAmount().compareTo(BigDecimal.ZERO) > 0) {
            hours = payment.getBaseAmount().divide(hourly, 1, RoundingMode.HALF_UP);
        }
        String rateLabel = hourly.setScale(0, RoundingMode.HALF_UP).toPlainString();
        String calculationLabel = switch (payment.getPaymentModel()) {
            case "HOURLY" -> hours.stripTrailingZeros().toPlainString() + "h \u00d7 \u20b9" + rateLabel;
            case "FIXED_MONTHLY" -> "\u20b9" + payment.getBaseAmount().setScale(0, RoundingMode.HALF_UP).toPlainString() + "/mo";
            default -> payment.getPaymentModel().replace('_', ' ').toLowerCase(Locale.ROOT);
        };
        BigDecimal balance = payment.getFinalPayable().subtract(payment.getAmountPaid()).max(BigDecimal.ZERO);
        return new SupporterPaymentDto(Ids.toJson(payment.getId()), Ids.toJson(payment.getExpertId()), name, code, group,
                payment.getBillingMonth(), payment.getPaymentModel(), calculationLabel, hours.toPlainString(), rate,
                payment.getBaseAmount().toPlainString(), payment.getAdjustmentAmount().toPlainString(),
                payment.getAdjustmentReason(), payment.getFinalPayable().toPlainString(), payment.getAmountPaid().toPlainString(),
                balance.toPlainString(), payment.getStatus(), payment.getNotes());
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void requireStaff() {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator()) {
            throw Exceptions.forbidden("Staff access required");
        }
    }

    private void requireStaffOrOwnExpert() {
        if (!currentUser.isAdmin() && !currentUser.isCoordinator() && !currentUser.isExpert()) {
            throw Exceptions.forbidden("Access denied");
        }
    }
}

@RestController
@RequestMapping("/api/v1/candidate-payments")
class CandidatePaymentController {
    private final PaymentTrackingService service;
    CandidatePaymentController(PaymentTrackingService service) { this.service = service; }

    @GetMapping
    public List<CandidatePaymentDto> list() { return service.listCandidatePayments(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidatePaymentDto create(@RequestBody CandidatePaymentRequest request) { return service.createCandidatePayment(request); }

    @PutMapping("/{id}")
    public CandidatePaymentDto update(@PathVariable String id, @RequestBody CandidatePaymentRequest request) {
        return service.updateCandidatePayment(id, request);
    }
}

@RestController
@RequestMapping("/api/v1/supporter-payments")
class SupporterPaymentController {
    private final PaymentTrackingService service;
    SupporterPaymentController(PaymentTrackingService service) { this.service = service; }

    @GetMapping
    public List<SupporterPaymentDto> list() { return service.listSupporterPayments(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupporterPaymentDto create(@RequestBody SupporterPaymentRequest request) { return service.createSupporterPayment(request); }

    @PutMapping("/{id}")
    public SupporterPaymentDto update(@PathVariable String id, @RequestBody SupporterPaymentRequest request) {
        return service.updateSupporterPayment(id, request);
    }
}
