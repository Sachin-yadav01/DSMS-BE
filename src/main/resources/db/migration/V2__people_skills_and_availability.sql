CREATE TABLE skill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    normalized_name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_normalized_name (normalized_name),
    CONSTRAINT chk_skill_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_skill_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_skill_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidate_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    display_code VARCHAR(16) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    country_code VARCHAR(8) NULL,
    experience_years INT NULL,
    support_goals TEXT NULL,
    preferred_days VARCHAR(120) NULL,
    preferred_times VARCHAR(255) NULL,
    employer_label VARCHAR(160) NULL,
    confidential_notes TEXT NULL,
    coordinator_user_id BIGINT NULL,
    onboarding_date DATE NULL,
    operational_notes TEXT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidate_user (user_id),
    UNIQUE KEY uk_candidate_display_code (display_code),
    KEY ix_candidate_coordinator (coordinator_user_id),
    KEY ix_candidate_status (status),
    CONSTRAINT chk_candidate_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_candidate_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_candidate_coordinator FOREIGN KEY (coordinator_user_id) REFERENCES user_account (id),
    CONSTRAINT fk_candidate_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_candidate_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE candidate_skill (
    candidate_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (candidate_id, skill_id),
    CONSTRAINT fk_candidate_skill_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (id),
    CONSTRAINT fk_candidate_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    display_code VARCHAR(16) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    experience_years INT NULL,
    profile_summary TEXT NULL,
    hourly_payout_rate DECIMAL(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expert_user (user_id),
    UNIQUE KEY uk_expert_display_code (display_code),
    KEY ix_expert_status (status),
    CONSTRAINT chk_expert_status CHECK (status IN ('AVAILABLE', 'FULLY_BOOKED', 'INACTIVE')),
    CONSTRAINT fk_expert_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_expert_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_expert_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_skill (
    expert_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    proficiency VARCHAR(16) NOT NULL,
    PRIMARY KEY (expert_id, skill_id),
    CONSTRAINT chk_expert_skill_proficiency CHECK (proficiency IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT fk_expert_skill_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_expert_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_availability_rule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_id BIGINT NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_local TIME NOT NULL,
    end_local TIME NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_expert_avail_rule (expert_id, day_of_week),
    CONSTRAINT chk_expert_avail_dow CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT fk_expert_avail_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_expert_avail_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_expert_avail_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE expert_availability_exception (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    reason VARCHAR(255) NULL,
    exception_type VARCHAR(16) NOT NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_expert_avail_ex (expert_id, start_at, end_at),
    CONSTRAINT chk_expert_avail_ex_type CHECK (exception_type IN ('LEAVE', 'UNAVAILABLE')),
    CONSTRAINT fk_expert_avail_ex_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_expert_avail_ex_created_by FOREIGN KEY (created_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
