CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    email_normalized VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    phone_country_code VARCHAR(8) NULL,
    phone_number VARCHAR(32) NULL,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at DATETIME(6) NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email_normalized (email_normalized),
    KEY ix_user_role_status (role, status),
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'COORDINATOR', 'CANDIDATE', 'EXPERT')),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INVITED')),
    CONSTRAINT fk_user_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_user_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_on DATETIME(6) NOT NULL,
    used_on DATETIME(6) NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY ix_password_reset_user (user_id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invitation_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_on DATETIME(6) NOT NULL,
    used_on DATETIME(6) NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitation_token_hash (token_hash),
    KEY ix_invitation_user (user_id),
    CONSTRAINT fk_invitation_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_invitation_created_by FOREIGN KEY (created_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE authentication_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email_normalized VARCHAR(255) NOT NULL,
    ip_address VARCHAR(64) NULL,
    success TINYINT(1) NOT NULL,
    attempted_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_auth_attempt_email_time (email_normalized, attempted_on),
    KEY ix_auth_attempt_ip_time (ip_address, attempted_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_setting (
    setting_key VARCHAR(100) NOT NULL,
    value_text VARCHAR(2000) NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    description VARCHAR(255) NULL,
    created_by BIGINT NULL,
    created_on DATETIME(6) NOT NULL,
    modified_by BIGINT NULL,
    modified_on DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (setting_key),
    CONSTRAINT chk_setting_type CHECK (value_type IN ('STRING', 'INT', 'BOOL', 'DECIMAL')),
    CONSTRAINT fk_setting_created_by FOREIGN KEY (created_by) REFERENCES user_account (id),
    CONSTRAINT fk_setting_modified_by FOREIGN KEY (modified_by) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_resource (resource_type, resource_id),
    KEY ix_audit_actor_time (actor_user_id, created_on),
    KEY ix_audit_action_time (action, created_on),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body JSON NULL,
    created_on DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_user_action_key (user_id, action, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE display_code_sequence (
    prefix VARCHAR(8) NOT NULL,
    next_value BIGINT NOT NULL,
    PRIMARY KEY (prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO display_code_sequence (prefix, next_value) VALUES
    ('CAN', 1),
    ('EXP', 1),
    ('ENG', 1),
    ('SES', 1),
    ('INV', 1),
    ('PAY', 1),
    ('REQ', 1),
    ('WRK', 1);
