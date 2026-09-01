ALTER TABLE app_user
    ADD COLUMN signature           VARCHAR(200) NULL,
    ADD COLUMN signature_pending   VARCHAR(200) NULL,
    ADD COLUMN signature_status    VARCHAR(20)  NOT NULL DEFAULT 'APPROVED';