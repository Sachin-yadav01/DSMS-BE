ALTER TABLE support_engagement DROP CHECK chk_engagement_status;
ALTER TABLE support_engagement
    ADD CONSTRAINT chk_engagement_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED'));
