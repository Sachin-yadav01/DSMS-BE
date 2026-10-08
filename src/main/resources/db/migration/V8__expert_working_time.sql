ALTER TABLE expert_profile
    ADD COLUMN working_time VARCHAR(16) NOT NULL DEFAULT 'BOTH' AFTER payment_type;

ALTER TABLE expert_profile
    ADD CONSTRAINT chk_expert_working_time CHECK (working_time IN ('MORNING', 'EVENING', 'BOTH'));
