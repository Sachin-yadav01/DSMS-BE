-- Phase 1: technology seed, candidate lifecycle, WhatsApp, expert status alignment
-- Phase 2: daily support markers (operational overrides; sheet itself is a view)

ALTER TABLE candidate_profile DROP CHECK chk_candidate_status;
UPDATE candidate_profile SET status = 'INACTIVE' WHERE status = 'ARCHIVED';
ALTER TABLE candidate_profile
    ADD COLUMN whatsapp_group_name VARCHAR(160) NULL AFTER employer_label,
    ADD COLUMN whatsapp_group_link VARCHAR(512) NULL AFTER whatsapp_group_name,
    ADD COLUMN primary_skill_id BIGINT NULL AFTER whatsapp_group_link,
    ADD COLUMN support_start_date DATE NULL AFTER onboarding_date,
    ADD COLUMN phone VARCHAR(40) NULL AFTER full_name,
    ADD COLUMN email VARCHAR(255) NULL AFTER phone;
ALTER TABLE candidate_profile
    ADD CONSTRAINT chk_candidate_status CHECK (status IN ('NOT_STARTED', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'INACTIVE')),
    ADD CONSTRAINT fk_candidate_primary_skill FOREIGN KEY (primary_skill_id) REFERENCES skill (id);

ALTER TABLE expert_profile DROP CHECK chk_expert_status;
UPDATE expert_profile SET status = CASE
    WHEN status = 'AVAILABLE' THEN 'ACTIVE'
    WHEN status = 'FULLY_BOOKED' THEN 'UNAVAILABLE'
    ELSE status
END;
ALTER TABLE expert_profile
    ADD COLUMN phone VARCHAR(40) NULL AFTER full_name,
    ADD COLUMN email VARCHAR(255) NULL AFTER phone,
    ADD COLUMN payment_type VARCHAR(32) NULL AFTER currency,
    ADD COLUMN notes TEXT NULL AFTER profile_summary;
ALTER TABLE expert_profile
    ADD CONSTRAINT chk_expert_status CHECK (status IN ('ACTIVE', 'UNAVAILABLE', 'INACTIVE'));

ALTER TABLE expert_skill DROP CHECK chk_expert_skill_proficiency;
ALTER TABLE expert_skill
    ADD CONSTRAINT chk_expert_skill_proficiency CHECK (proficiency IN ('PRIMARY', 'BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'));

INSERT INTO skill (name, normalized_name, status, created_on, modified_on, version)
SELECT v.name, v.normalized_name, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
FROM (
    SELECT 'Java' AS name, 'java' AS normalized_name UNION ALL
    SELECT 'Spring Boot', 'spring boot' UNION ALL
    SELECT 'React', 'react' UNION ALL
    SELECT 'Angular', 'angular' UNION ALL
    SELECT 'AWS', 'aws' UNION ALL
    SELECT 'DevOps', 'devops' UNION ALL
    SELECT 'Python', 'python' UNION ALL
    SELECT 'Data Engineering', 'data engineering' UNION ALL
    SELECT '.NET', '.net' UNION ALL
    SELECT 'QA', 'qa' UNION ALL
    SELECT 'Other', 'other'
) v
WHERE NOT EXISTS (SELECT 1 FROM skill s WHERE s.normalized_name = v.normalized_name);

CREATE TABLE daily_support_marker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL,
    support_date DATE NOT NULL,
    day_status VARCHAR(32) NOT NULL,
    notes TEXT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_marker (candidate_id, support_date),
    CONSTRAINT chk_daily_marker_status CHECK (day_status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'NO_SUPPORT_REQUIRED',
        'CANDIDATE_UNAVAILABLE', 'SUPPORTER_UNAVAILABLE', 'MISSED'
    )),
    CONSTRAINT fk_daily_marker_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (id),
    CONSTRAINT fk_daily_marker_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_daily_marker_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidate_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    candidate_id BIGINT NOT NULL,
    billing_month CHAR(7) NOT NULL,
    expected_amount DECIMAL(12, 2) NOT NULL,
    paid_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    due_date DATE NULL,
    payment_date DATE NULL,
    method VARCHAR(40) NULL,
    external_reference VARCHAR(120) NULL,
    notes TEXT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_candidate_payment_candidate (candidate_id),
    KEY ix_candidate_payment_month (billing_month),
    CONSTRAINT chk_candidate_payment_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE', 'WAIVED')),
    CONSTRAINT fk_candidate_payment_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (id),
    CONSTRAINT fk_candidate_payment_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_candidate_payment_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supporter_payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_id BIGINT NOT NULL,
    billing_month CHAR(7) NOT NULL,
    payment_model VARCHAR(32) NOT NULL,
    base_amount DECIMAL(12, 2) NOT NULL,
    adjustment_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    adjustment_reason VARCHAR(255) NULL,
    final_payable DECIMAL(12, 2) NOT NULL,
    amount_paid DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    notes TEXT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_supporter_payment_expert (expert_id),
    KEY ix_supporter_payment_month (billing_month),
    CONSTRAINT chk_supporter_payment_model CHECK (payment_model IN ('HOURLY', 'FIXED_MONTHLY', 'PER_CANDIDATE', 'CUSTOM')),
    CONSTRAINT chk_supporter_payment_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE', 'WAIVED')),
    CONSTRAINT fk_supporter_payment_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_supporter_payment_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_supporter_payment_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
