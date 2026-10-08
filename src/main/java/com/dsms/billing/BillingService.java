package com.dsms.billing;

import com.dsms.audit.AuditService;
import com.dsms.candidates.CandidateProfileRepository;
import com.dsms.candidates.ExpertProfileRepository;
import com.dsms.common.api.Ids;
import com.dsms.common.config.DsmsProperties;
import com.dsms.common.exception.Exceptions;
import com.dsms.common.persistence.DisplayCodeService;
import com.dsms.common.security.CurrentUser;
import com.dsms.engagements.ExpertAssignment;
import com.dsms.notifications.NotificationService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Getter @Setter @Entity @Table(name = "invoice_line")
class InvoiceLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long invoiceId;
    private Long billingPeriodId;
    @Column(nullable = false) private String description;
    private Integer quantitySeconds;
    private BigDecimal rate;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String lineType;
    @Column(nullable = false) private java.time.Instant createdOn;
}

@Getter @Setter @Entity @Table(name = "invoice_allocation_reservation")
class InvoiceReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long invoiceId;
    @Column(nullable = false) private Long allocationId;
    @Column(nullable = false) private String status;
    private java.time.Instant releasedOn;
    @Column(nullable = false) private java.time.Instant createdOn;
    @Column(insertable = false, updatable = false) private Long activeAllocationId;
}

@Getter @Setter @Entity @Table(name = "payment_receipt")
class PaymentReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long invoiceId;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private LocalDate paymentDate;
    @Column(nullable = false) private String method;
    private String externalReference;
    private String note;
    @Column(nullable = false) private String idempotencyKey;
    @Column(nullable = false) private Long actorUserId;
    private java.time.Instant reversedOn;
    @Column(nullable = false) private java.time.Instant createdOn;
}

@Getter @Setter @Entity @Table(name = "payout_line")
class PayoutLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long statementId;
    private Long billingPeriodId;
    @Column(nullable = false) private String description;
    private Integer quantitySeconds;
    private BigDecimal rate;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String lineType;
    @Column(nullable = false) private java.time.Instant createdOn;
}

@Getter @Setter @Entity @Table(name = "payout_allocation_reservation")
class PayoutReservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long statementId;
    @Column(nullable = false) private Long allocationId;
    @Column(nullable = false) private String status;
    private java.time.Instant releasedOn;
    @Column(nullable = false) private java.time.Instant createdOn;
    @Column(insertable = false, updatable = false) private Long activeAllocationId;
}

@Getter @Setter @Entity @Table(name = "payout_payment")
class PayoutPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long statementId;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private LocalDate paymentDate;
    @Column(nullable = false) private String method;
    private String externalReference;
    private String note;
    @Column(nullable = false) private String idempotencyKey;
    @Column(nullable = false) private Long actorUserId;
    private java.time.Instant reversedOn;
    @Column(nullable = false) private java.time.Instant createdOn;
}

@Repository interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    List<InvoiceLine> findByInvoiceId(Long invoiceId);
}
@Repository interface InvoiceReservationRepository extends JpaRepository<InvoiceReservation, Long> {
    boolean existsByActiveAllocationId(Long allocationId);
}
@Repository interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {
    List<PaymentReceipt> findByInvoiceId(Long invoiceId);
}
@Repository interface PayoutLineRepository extends JpaRepository<PayoutLine, Long> {
    List<PayoutLine> findByStatementId(Long statementId);
}
@Repository interface PayoutReservationRepository extends JpaRepository<PayoutReservation, Long> {
    boolean existsByActiveAllocationId(Long allocationId);
}
@Repository interface PayoutPaymentRepository extends JpaRepository<PayoutPayment, Long> {
    List<PayoutPayment> findByStatementId(Long statementId);
}

record InvoiceDto(String id, String invoiceNumber, String candidateId, String status, String currency, String total,
                         String collectedAmount, String outstanding, String paymentStatus) {}
record PayoutDto(String id, String statementNumber, String expertId, String status, String currency, String total, String paidAmount) {}
record PaymentRequest(String amount, String paymentDate, String method, String externalReference, String note, String idempotencyKey) {}
record GenerateRequest(String candidateId, String expertId) {}

@Service
public class BillingService {
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final InvoiceReservationRepository invoiceReservations;
    private final PaymentReceiptRepository receipts;
    private final PayoutStatementRepository payouts;
    private final PayoutLineRepository payoutLines;
    private final PayoutReservationRepository payoutReservations;
    private final PayoutPaymentRepository payoutPayments;
    private final ApprovedTimeAllocationRepository allocations;
    private final BillingPeriodRepository periods;
    private final CurrentUser currentUser;
    private final DisplayCodeService codes;
    private final DsmsProperties properties;
    private final Clock clock;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final CandidateProfileRepository candidates;
    private final ExpertProfileRepository experts;
    private final com.dsms.engagements.ExpertAssignmentLookup assignments;

    public BillingService(InvoiceRepository invoices, InvoiceLineRepository invoiceLines, InvoiceReservationRepository invoiceReservations,
                          PaymentReceiptRepository receipts, PayoutStatementRepository payouts, PayoutLineRepository payoutLines,
                          PayoutReservationRepository payoutReservations, PayoutPaymentRepository payoutPayments,
                          ApprovedTimeAllocationRepository allocations, BillingPeriodRepository periods, CurrentUser currentUser,
                          DisplayCodeService codes, DsmsProperties properties, Clock clock, AuditService auditService,
                          NotificationService notifications, CandidateProfileRepository candidates, ExpertProfileRepository experts,
                          com.dsms.engagements.ExpertAssignmentLookup assignments) {
        this.invoices = invoices;
        this.invoiceLines = invoiceLines;
        this.invoiceReservations = invoiceReservations;
        this.receipts = receipts;
        this.payouts = payouts;
        this.payoutLines = payoutLines;
        this.payoutReservations = payoutReservations;
        this.payoutPayments = payoutPayments;
        this.allocations = allocations;
        this.periods = periods;
        this.currentUser = currentUser;
        this.codes = codes;
        this.properties = properties;
        this.clock = clock;
        this.auditService = auditService;
        this.notifications = notifications;
        this.candidates = candidates;
        this.experts = experts;
        this.assignments = assignments;
    }

    @Transactional
    public InvoiceDto generateInvoice(String candidateId) {
        requireAdmin();
        Long cid = Ids.parse(candidateId);
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(codes.next("INV"));
        invoice.setCandidateId(cid);
        invoice.setStatus("DRAFT");
        invoice.setCurrency(properties.getCurrency());
        invoice.setSubtotal(Money.zero());
        invoice.setAdjustmentTotal(Money.zero());
        invoice.setTotal(Money.zero());
        invoice.setCollectedAmount(Money.zero());
        invoices.save(invoice);
        BigDecimal subtotal = Money.zero();
        for (ApprovedTimeAllocation allocation : allocations.findByCandidateId(cid)) {
            if (invoiceReservations.existsByActiveAllocationId(allocation.getId())) {
                continue;
            }
            BillingPeriod period = periods.findById(allocation.getBillingPeriodId()).orElseThrow();
            BigDecimal rate = period.getHourlyRate() == null ? Money.zero() : period.getHourlyRate();
            BigDecimal amount;
            String type;
            String description;
            if ("MONTHLY".equals(period.getPlanType())) {
                boolean baseExists = invoiceLines.findByInvoiceId(invoice.getId()).stream()
                        .anyMatch(l -> period.getId().equals(l.getBillingPeriodId()) && "MONTHLY_BASE".equals(l.getLineType()));
                if (!baseExists && period.getConfirmedBaseFee() != null) {
                    InvoiceLine base = line(invoice.getId(), period.getId(), "Monthly support fee", null, null, period.getConfirmedBaseFee(), "MONTHLY_BASE");
                    invoiceLines.save(base);
                    subtotal = subtotal.add(base.getAmount());
                }
                int included = period.getConfirmedIncludedSeconds() == null ? 0 : period.getConfirmedIncludedSeconds();
                int billable = allocation.getBillableSeconds();
                // overage handled as remaining excess if enabled; otherwise still no extra charge
                amount = Money.zero();
                type = "HOURLY_TIME";
                description = "Approved billable time";
                if (period.isOverageEnabled() && period.getOverageHourlyRate() != null) {
                    int previously = allocations.findByBillingPeriodId(period.getId()).stream().mapToInt(ApprovedTimeAllocation::getBillableSeconds).sum();
                    int excess = Math.max(0, previously - included);
                    amount = Money.ofHours(Math.min(billable, excess), period.getOverageHourlyRate());
                    type = "OVERAGE";
                    description = "Authorized overage";
                }
            } else {
                amount = Money.ofHours(allocation.getBillableSeconds(), rate);
                type = "HOURLY_TIME";
                description = "Approved billable time";
            }
            InvoiceLine line = line(invoice.getId(), period.getId(), description, allocation.getBillableSeconds(), rate, amount, type);
            invoiceLines.save(line);
            subtotal = subtotal.add(line.getAmount());
            InvoiceReservation reservation = new InvoiceReservation();
            reservation.setInvoiceId(invoice.getId());
            reservation.setAllocationId(allocation.getId());
            reservation.setStatus("ACTIVE");
            reservation.setCreatedOn(clock.instant());
            invoiceReservations.save(reservation);
        }
        invoice.setSubtotal(subtotal);
        invoice.setTotal(subtotal);
        return toInvoice(invoice);
    }

    @Transactional
    public InvoiceDto issue(String id) {
        requireAdmin();
        Invoice invoice = invoices.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Invoice not found"));
        invoice.setStatus("ISSUED");
        invoice.setIssueDate(LocalDate.now(clock));
        invoice.setDueDate(LocalDate.now(clock).plusDays(14));
        auditService.record("INVOICE_ISSUE", "invoice", id, null, java.util.Map.of("number", invoice.getInvoiceNumber()));
        var candidate = candidates.findById(invoice.getCandidateId()).orElseThrow();
        notifications.notifyUser(candidate.getUserId(), "Invoice issued", invoice.getInvoiceNumber(), "/invoices/" + id);
        return toInvoice(invoice);
    }

    @Transactional
    public InvoiceDto receivePayment(String id, PaymentRequest request) {
        requireAdmin();
        Invoice invoice = invoices.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Invoice not found"));
        if (!"ISSUED".equals(invoice.getStatus())) {
            throw Exceptions.conflict("INVOICE_NOT_ISSUED", "Payments can only be recorded against issued invoices");
        }
        BigDecimal amount = new BigDecimal(request.amount());
        BigDecimal outstanding = invoice.getTotal().subtract(invoice.getCollectedAmount());
        if (amount.compareTo(outstanding) > 0) {
            throw Exceptions.conflict("OVERPAY", "Payment exceeds outstanding amount");
        }
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setInvoiceId(invoice.getId());
        receipt.setAmount(amount);
        receipt.setPaymentDate(LocalDate.parse(request.paymentDate()));
        receipt.setMethod(request.method());
        receipt.setExternalReference(request.externalReference());
        receipt.setNote(request.note());
        receipt.setIdempotencyKey(request.idempotencyKey() == null ? java.util.UUID.randomUUID().toString() : request.idempotencyKey());
        receipt.setActorUserId(currentUser.requireUserId());
        receipt.setCreatedOn(clock.instant());
        receipts.save(receipt);
        invoice.setCollectedAmount(invoice.getCollectedAmount().add(amount));
        auditService.record("PAYMENT_RECEIPT", "payment_receipt", Ids.toJson(receipt.getId()), null, java.util.Map.of("amount", amount.toPlainString()));
        return toInvoice(invoice);
    }

    @Transactional
    public PayoutDto generatePayout(String expertId) {
        requireAdmin();
        Long eid = Ids.parse(expertId);
        PayoutStatement statement = new PayoutStatement();
        statement.setStatementNumber(codes.next("PAY"));
        statement.setExpertId(eid);
        statement.setStatus("DRAFT");
        statement.setCurrency(properties.getCurrency());
        statement.setSubtotal(Money.zero());
        statement.setAdjustmentTotal(Money.zero());
        statement.setTotal(Money.zero());
        statement.setPaidAmount(Money.zero());
        payouts.save(statement);
        BigDecimal subtotal = Money.zero();
        for (ApprovedTimeAllocation allocation : allocations.findByExpertId(eid)) {
            if (payoutReservations.existsByActiveAllocationId(allocation.getId())) {
                continue;
            }
            ExpertAssignment assignment = assignments.get(allocation.getAssignmentId());
            BigDecimal amount = Money.ofHours(allocation.getPayableSeconds(), assignment.getHourlyRateSnapshot());
            PayoutLine line = new PayoutLine();
            line.setStatementId(statement.getId());
            line.setBillingPeriodId(allocation.getBillingPeriodId());
            line.setDescription("Approved payable time");
            line.setQuantitySeconds(allocation.getPayableSeconds());
            line.setRate(assignment.getHourlyRateSnapshot());
            line.setAmount(amount);
            line.setLineType("HOURLY_TIME");
            line.setCreatedOn(clock.instant());
            payoutLines.save(line);
            subtotal = subtotal.add(amount);
            PayoutReservation reservation = new PayoutReservation();
            reservation.setStatementId(statement.getId());
            reservation.setAllocationId(allocation.getId());
            reservation.setStatus("ACTIVE");
            reservation.setCreatedOn(clock.instant());
            payoutReservations.save(reservation);
        }
        statement.setSubtotal(subtotal);
        statement.setTotal(subtotal);
        return toPayout(statement);
    }

    @Transactional
    public PayoutDto issuePayout(String id) {
        requireAdmin();
        PayoutStatement statement = payouts.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Statement not found"));
        statement.setStatus("ISSUED");
        statement.setIssueDate(LocalDate.now(clock));
        return toPayout(statement);
    }

    @Transactional
    public PayoutDto payPayout(String id, PaymentRequest request) {
        requireAdmin();
        PayoutStatement statement = payouts.findById(Ids.parse(id)).orElseThrow(() -> Exceptions.notFound("Statement not found"));
        BigDecimal amount = new BigDecimal(request.amount());
        BigDecimal outstanding = statement.getTotal().subtract(statement.getPaidAmount());
        if (amount.compareTo(outstanding) > 0) {
            throw Exceptions.conflict("OVERPAY", "Payout exceeds outstanding earnings");
        }
        PayoutPayment payment = new PayoutPayment();
        payment.setStatementId(statement.getId());
        payment.setAmount(amount);
        payment.setPaymentDate(LocalDate.parse(request.paymentDate()));
        payment.setMethod(request.method());
        payment.setExternalReference(request.externalReference());
        payment.setNote(request.note());
        payment.setIdempotencyKey(request.idempotencyKey() == null ? java.util.UUID.randomUUID().toString() : request.idempotencyKey());
        payment.setActorUserId(currentUser.requireUserId());
        payment.setCreatedOn(clock.instant());
        payoutPayments.save(payment);
        statement.setPaidAmount(statement.getPaidAmount().add(amount));
        return toPayout(statement);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> listInvoices() {
        if (currentUser.isAdmin()) {
            return invoices.findAll().stream().map(this::toInvoice).toList();
        }
        if (currentUser.isCandidate()) {
            return candidates.findByUserId(currentUser.requireUserId())
                    .map(c -> invoices.findByCandidateIdOrderByCreatedOnDesc(c.getId()).stream().map(this::toInvoice).toList())
                    .orElse(List.of());
        }
        throw Exceptions.forbidden("Cannot view invoices");
    }

    @Transactional(readOnly = true)
    public List<PayoutDto> listPayouts() {
        if (currentUser.isAdmin()) {
            return payouts.findAll().stream().map(this::toPayout).toList();
        }
        if (currentUser.isExpert()) {
            return experts.findByUserId(currentUser.requireUserId())
                    .map(e -> payouts.findByExpertIdOrderByCreatedOnDesc(e.getId()).stream().map(this::toPayout).toList())
                    .orElse(List.of());
        }
        throw Exceptions.forbidden("Cannot view payouts");
    }

    private InvoiceLine line(Long invoiceId, Long periodId, String description, Integer seconds, BigDecimal rate, BigDecimal amount, String type) {
        InvoiceLine line = new InvoiceLine();
        line.setInvoiceId(invoiceId);
        line.setBillingPeriodId(periodId);
        line.setDescription(description);
        line.setQuantitySeconds(seconds);
        line.setRate(rate);
        line.setAmount(amount);
        line.setLineType(type);
        line.setCreatedOn(clock.instant());
        return line;
    }

    private InvoiceDto toInvoice(Invoice invoice) {
        BigDecimal outstanding = invoice.getTotal().subtract(invoice.getCollectedAmount());
        String paymentStatus = invoice.getCollectedAmount().signum() == 0 ? "UNPAID"
                : outstanding.signum() == 0 ? "PAID" : "PARTIALLY_PAID";
        return new InvoiceDto(Ids.toJson(invoice.getId()), invoice.getInvoiceNumber(), Ids.toJson(invoice.getCandidateId()),
                invoice.getStatus(), invoice.getCurrency(), invoice.getTotal().toPlainString(),
                invoice.getCollectedAmount().toPlainString(), outstanding.toPlainString(), paymentStatus);
    }

    private PayoutDto toPayout(PayoutStatement statement) {
        return new PayoutDto(Ids.toJson(statement.getId()), statement.getStatementNumber(), Ids.toJson(statement.getExpertId()),
                statement.getStatus(), statement.getCurrency(), statement.getTotal().toPlainString(), statement.getPaidAmount().toPlainString());
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw Exceptions.forbidden("Admin access required");
        }
    }
}

@RestController
@RequestMapping("/api/v1/invoices")
class InvoiceController {
    private final BillingService billing;
    InvoiceController(BillingService billing) { this.billing = billing; }

    @GetMapping
    public List<InvoiceDto> list() { return billing.listInvoices(); }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDto generate(@RequestBody GenerateRequest request) { return billing.generateInvoice(request.candidateId()); }

    @PostMapping("/{id}/issue")
    public InvoiceDto issue(@PathVariable String id) { return billing.issue(id); }

    @PostMapping("/{id}/receipts")
    public InvoiceDto pay(@PathVariable String id, @RequestBody PaymentRequest request) { return billing.receivePayment(id, request); }
}

@RestController
@RequestMapping("/api/v1/payout-statements")
class PayoutController {
    private final BillingService billing;
    PayoutController(BillingService billing) { this.billing = billing; }

    @GetMapping
    public List<PayoutDto> list() { return billing.listPayouts(); }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutDto generate(@RequestBody GenerateRequest request) { return billing.generatePayout(request.expertId()); }

    @PostMapping("/{id}/issue")
    public PayoutDto issue(@PathVariable String id) { return billing.issuePayout(id); }

    @PostMapping("/{id}/payments")
    public PayoutDto pay(@PathVariable String id, @RequestBody PaymentRequest request) { return billing.payPayout(id, request); }
}
