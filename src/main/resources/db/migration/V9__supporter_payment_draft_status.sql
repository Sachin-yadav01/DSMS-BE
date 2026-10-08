ALTER TABLE supporter_payment DROP CHECK chk_supporter_payment_status;
ALTER TABLE supporter_payment
    ADD CONSTRAINT chk_supporter_payment_status CHECK (status IN ('DRAFT', 'PENDING', 'PARTIAL', 'PAID', 'OVERDUE', 'WAIVED'));
