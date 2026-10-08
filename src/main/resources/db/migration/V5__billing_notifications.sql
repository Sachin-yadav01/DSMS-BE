CREATE TABLE billing_period (
    id BIGINT NOT NULL AUTO_INCREMENT,
    engagement_id BIGINT NOT NULL,
    period_start DATETIME(6) NOT NULL,
    period_end DATETIME(6) NOT NULL,
    business_timezone VARCHAR(64) NOT NULL,
    terms_id BIGINT NOT NULL,
    plan_type VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    confirmed_base_fee DECIMAL(12, 2) NULL,
    confirmed_included_seconds INT NULL,
    overage_enabled TINYINT(1) NOT NULL DEFAULT 0,
    overage_hourly_rate DECIMAL(12, 2) NULL,
    hourly_rate DECIMAL(12, 2) NULL,
    partial_month TINYINT(1) NOT NULL DEFAULT 0,
    terms_confirmed TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_period_identity (engagement_id, period_start),
    CONSTRAINT chk_billing_period_plan CHECK (plan_type IN ('HOURLY', 'MONTHLY')),
    CONSTRAINT chk_billing_period_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT fk_billing_period_engagement FOREIGN KEY (engagement_id) REFERENCES support_engagement (id),
    CONSTRAINT fk_billing_period_terms FOREIGN KEY (terms_id) REFERENCES engagement_terms (id),
    CONSTRAINT fk_billing_period_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_billing_period_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE approved_time_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_entry_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    billing_period_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    expert_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    approved_actual_seconds INT NOT NULL,
    billable_seconds INT NOT NULL,
    payable_seconds INT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_revision_period (revision_id, billing_period_id),
    KEY ix_allocation_period (billing_period_id),
    KEY ix_allocation_expert (expert_id),
    CONSTRAINT fk_alloc_entry FOREIGN KEY (work_entry_id) REFERENCES work_entry (id),
    CONSTRAINT fk_alloc_revision FOREIGN KEY (revision_id) REFERENCES work_entry_revision (id),
    CONSTRAINT fk_alloc_period FOREIGN KEY (billing_period_id) REFERENCES billing_period (id),
    CONSTRAINT fk_alloc_assignment FOREIGN KEY (assignment_id) REFERENCES expert_assignment (id),
    CONSTRAINT fk_alloc_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_alloc_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_number VARCHAR(24) NOT NULL,
    candidate_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    issue_date DATE NULL,
    due_date DATE NULL,
    subtotal DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    adjustment_total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    collected_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_number (invoice_number),
    KEY ix_invoice_candidate_status (candidate_id, status),
    KEY ix_invoice_due (due_date, status),
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'VOID')),
    CONSTRAINT fk_invoice_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (id),
    CONSTRAINT fk_invoice_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_invoice_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    billing_period_id BIGINT NULL,
    description VARCHAR(255) NOT NULL,
    quantity_seconds INT NULL,
    rate DECIMAL(12, 2) NULL,
    amount DECIMAL(14, 2) NOT NULL,
    line_type VARCHAR(24) NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_invoice_line (invoice_id),
    CONSTRAINT chk_invoice_line_type CHECK (line_type IN ('HOURLY_TIME', 'MONTHLY_BASE', 'OVERAGE', 'ADJUSTMENT')),
    CONSTRAINT fk_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT fk_invoice_line_period FOREIGN KEY (billing_period_id) REFERENCES billing_period (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_allocation_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    allocation_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    released_on DATETIME(6) NULL,
    created_on DATETIME(6) NOT NULL,
    active_allocation_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN allocation_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_active_alloc (active_allocation_id),
    KEY ix_invoice_res_invoice (invoice_id),
    CONSTRAINT chk_invoice_res_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT fk_invoice_res_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT fk_invoice_res_alloc FOREIGN KEY (allocation_id) REFERENCES approved_time_allocation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    payment_date DATE NOT NULL,
    method VARCHAR(16) NOT NULL,
    external_reference VARCHAR(80) NULL,
    note VARCHAR(500) NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reversed_on DATETIME(6) NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_idempotency (invoice_id, idempotency_key),
    KEY ix_receipt_invoice (invoice_id),
    CONSTRAINT chk_receipt_method CHECK (method IN ('BANK', 'UPI', 'CASH', 'OTHER')),
    CONSTRAINT fk_receipt_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT fk_receipt_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE receipt_reversal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reversal_type VARCHAR(16) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_reversal (receipt_id),
    CONSTRAINT chk_receipt_rev_type CHECK (reversal_type IN ('CORRECTION', 'REFUND')),
    CONSTRAINT fk_receipt_rev_receipt FOREIGN KEY (receipt_id) REFERENCES payment_receipt (id),
    CONSTRAINT fk_receipt_rev_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payout_statement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    statement_number VARCHAR(24) NOT NULL,
    expert_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    issue_date DATE NULL,
    subtotal DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    adjustment_total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payout_number (statement_number),
    KEY ix_payout_expert_status (expert_id, status),
    CONSTRAINT chk_payout_status CHECK (status IN ('DRAFT', 'ISSUED', 'VOID')),
    CONSTRAINT fk_payout_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_payout_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_payout_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payout_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    billing_period_id BIGINT NULL,
    description VARCHAR(255) NOT NULL,
    quantity_seconds INT NULL,
    rate DECIMAL(12, 2) NULL,
    amount DECIMAL(14, 2) NOT NULL,
    line_type VARCHAR(24) NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_payout_line (statement_id),
    CONSTRAINT chk_payout_line_type CHECK (line_type IN ('HOURLY_TIME', 'ADJUSTMENT')),
    CONSTRAINT fk_payout_line_statement FOREIGN KEY (statement_id) REFERENCES payout_statement (id),
    CONSTRAINT fk_payout_line_period FOREIGN KEY (billing_period_id) REFERENCES billing_period (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payout_allocation_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    allocation_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    released_on DATETIME(6) NULL,
    created_on DATETIME(6) NOT NULL,
    active_allocation_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN allocation_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payout_active_alloc (active_allocation_id),
    KEY ix_payout_res_statement (statement_id),
    CONSTRAINT chk_payout_res_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT fk_payout_res_statement FOREIGN KEY (statement_id) REFERENCES payout_statement (id),
    CONSTRAINT fk_payout_res_alloc FOREIGN KEY (allocation_id) REFERENCES approved_time_allocation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payout_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    payment_date DATE NOT NULL,
    method VARCHAR(16) NOT NULL,
    external_reference VARCHAR(80) NULL,
    note VARCHAR(500) NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    reversed_on DATETIME(6) NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payout_pay_idempotency (statement_id, idempotency_key),
    KEY ix_payout_pay_statement (statement_id),
    CONSTRAINT chk_payout_pay_method CHECK (method IN ('BANK', 'UPI', 'CASH', 'OTHER')),
    CONSTRAINT fk_payout_pay_statement FOREIGN KEY (statement_id) REFERENCES payout_statement (id),
    CONSTRAINT fk_payout_pay_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payout_reversal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payout_payment_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reversal_type VARCHAR(16) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payout_reversal (payout_payment_id),
    CONSTRAINT chk_payout_rev_type CHECK (reversal_type IN ('CORRECTION', 'REFUND')),
    CONSTRAINT fk_payout_rev_payment FOREIGN KEY (payout_payment_id) REFERENCES payout_payment (id),
    CONSTRAINT fk_payout_rev_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    work_entry_id BIGINT NULL,
    actor_user_id BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_invoice_adj (invoice_id),
    CONSTRAINT fk_invoice_adj_invoice FOREIGN KEY (invoice_id) REFERENCES invoice (id),
    CONSTRAINT fk_invoice_adj_work FOREIGN KEY (work_entry_id) REFERENCES work_entry (id),
    CONSTRAINT fk_invoice_adj_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payout_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    statement_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    work_entry_id BIGINT NULL,
    actor_user_id BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_payout_adj (statement_id),
    CONSTRAINT fk_payout_adj_statement FOREIGN KEY (statement_id) REFERENCES payout_statement (id),
    CONSTRAINT fk_payout_adj_work FOREIGN KEY (work_entry_id) REFERENCES work_entry (id),
    CONSTRAINT fk_payout_adj_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    link_path VARCHAR(255) NULL,
    read_on DATETIME(6) NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_notification_unread (user_id, read_on, created_on),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    dedup_key VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_on DATETIME(6) NOT NULL,
    last_error VARCHAR(500) NULL,
    created_on DATETIME(6) NOT NULL,
    processed_on DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_dedup (dedup_key),
    KEY ix_outbox_due (status, next_attempt_on),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
