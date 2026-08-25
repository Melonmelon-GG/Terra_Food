ALTER TABLE app_user
    ADD COLUMN email VARCHAR(254) NULL AFTER display_name,
    ADD CONSTRAINT uk_app_user_email UNIQUE (email);
