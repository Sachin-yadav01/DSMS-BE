CREATE TABLE support_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    display_code VARCHAR(16) NOT NULL,
    engagement_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    assignee_user_id BIGINT NULL,
    target_date DATE NULL,
    outside_meeting_work_enabled TINYINT(1) NOT NULL DEFAULT 0,
    outcome_summary VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_display_code (display_code),
    KEY ix_request_engagement_status (engagement_id, status),
    CONSTRAINT chk_request_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_request_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_CANDIDATE', 'RESOLVED', 'CLOSED')),
    CONSTRAINT fk_request_engagement FOREIGN KEY (engagement_id) REFERENCES support_engagement (id),
    CONSTRAINT fk_request_assignee FOREIGN KEY (assignee_user_id) REFERENCES user_account (id),
    CONSTRAINT fk_request_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_request_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_request_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    body TEXT NOT NULL,
    author_user_id BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_request_comment (request_id, created_on),
    CONSTRAINT chk_request_comment_vis CHECK (visibility IN ('SHARED', 'INTERNAL')),
    CONSTRAINT fk_request_comment_request FOREIGN KEY (request_id) REFERENCES support_request (id),
    CONSTRAINT fk_request_comment_author FOREIGN KEY (author_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    storage_key VARCHAR(80) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    parent_type VARCHAR(32) NOT NULL,
    parent_id BIGINT NOT NULL,
    archived_on DATETIME(6) NULL,
    uploaded_by BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attachment_storage_key (storage_key),
    KEY ix_attachment_parent (parent_type, parent_id),
    CONSTRAINT chk_attachment_parent CHECK (parent_type IN ('SUPPORT_REQUEST', 'SUPPORT_REQUEST_COMMENT', 'WORK_ENTRY')),
    CONSTRAINT fk_attachment_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    display_code VARCHAR(16) NOT NULL,
    engagement_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    expert_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    request_id BIGINT NULL,
    source VARCHAR(16) NOT NULL,
    category VARCHAR(16) NOT NULL,
    timer_state VARCHAR(16) NULL,
    recorded_seconds INT NOT NULL DEFAULT 0,
    shared_summary VARCHAR(1000) NULL,
    manual_reason VARCHAR(500) NULL,
    review_flag TINYINT(1) NOT NULL DEFAULT 0,
    long_session_flag TINYINT(1) NOT NULL DEFAULT 0,
    boundary_flag TINYINT(1) NOT NULL DEFAULT 0,
    effective_revision_id BIGINT NULL,
    force_stopped TINYINT(1) NOT NULL DEFAULT 0,
    force_stop_reason VARCHAR(500) NULL,
    open_expert_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN timer_state IN ('RUNNING', 'PAUSED') THEN expert_id ELSE NULL END
    ) STORED,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_display_code (display_code),
    UNIQUE KEY uk_work_open_expert (open_expert_id),
    KEY ix_work_expert_created (expert_id, created_on),
    KEY ix_work_engagement (engagement_id),
    KEY ix_work_candidate (candidate_id),
    CONSTRAINT chk_work_source CHECK (source IN ('TIMER', 'MANUAL')),
    CONSTRAINT chk_work_category CHECK (category IN ('MEETING', 'INVESTIGATION', 'PREPARATION', 'FOLLOW_UP')),
    CONSTRAINT chk_work_timer_state CHECK (timer_state IS NULL OR timer_state IN ('RUNNING', 'PAUSED', 'STOPPED')),
    CONSTRAINT fk_work_engagement FOREIGN KEY (engagement_id) REFERENCES support_engagement (id),
    CONSTRAINT fk_work_assignment FOREIGN KEY (assignment_id) REFERENCES expert_assignment (id),
    CONSTRAINT fk_work_expert FOREIGN KEY (expert_id) REFERENCES expert_profile (id),
    CONSTRAINT fk_work_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_profile (id),
    CONSTRAINT fk_work_session FOREIGN KEY (session_id) REFERENCES support_session (id),
    CONSTRAINT fk_work_request FOREIGN KEY (request_id) REFERENCES support_request (id),
    CONSTRAINT fk_work_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_work_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_entry_id BIGINT NOT NULL,
    started_on DATETIME(6) NOT NULL,
    ended_on DATETIME(6) NULL,
    pause_reason VARCHAR(255) NULL,
    open_entry_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN ended_on IS NULL THEN work_entry_id ELSE NULL END
    ) STORED,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_open_segment (open_entry_id),
    KEY ix_work_segment_entry (work_entry_id, started_on),
    CONSTRAINT fk_work_segment_entry FOREIGN KEY (work_entry_id) REFERENCES work_entry (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_entry_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_entry_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    proposed_seconds INT NOT NULL,
    approved_actual_seconds INT NULL,
    billable_seconds INT NULL,
    payable_seconds INT NULL,
    shared_summary VARCHAR(1000) NULL,
    correction_reason VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    created_on DATETIME(6) NOT NULL,
    superseded_on DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_revision_no (work_entry_id, revision_no),
    KEY ix_work_revision_status (status),
    CONSTRAINT chk_work_revision_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'CANDIDATE_CONFIRMED', 'APPROVED', 'DISPUTED', 'REJECTED', 'SUPERSEDED'
    )),
    CONSTRAINT fk_work_revision_entry FOREIGN KEY (work_entry_id) REFERENCES work_entry (id),
    CONSTRAINT fk_work_revision_created_by FOREIGN KEY (created_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE work_entry
    ADD CONSTRAINT fk_work_effective_revision FOREIGN KEY (effective_revision_id) REFERENCES work_entry_revision (id);

CREATE TABLE work_revision_segment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_id BIGINT NOT NULL,
    started_on DATETIME(6) NOT NULL,
    ended_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_rev_segment (revision_id, started_on),
    CONSTRAINT fk_rev_segment_revision FOREIGN KEY (revision_id) REFERENCES work_entry_revision (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE work_review_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_entry_id BIGINT NOT NULL,
    revision_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NULL,
    actor_user_id BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_work_review (work_entry_id, created_on),
    CONSTRAINT fk_work_review_entry FOREIGN KEY (work_entry_id) REFERENCES work_entry (id),
    CONSTRAINT fk_work_review_revision FOREIGN KEY (revision_id) REFERENCES work_entry_revision (id),
    CONSTRAINT fk_work_review_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
