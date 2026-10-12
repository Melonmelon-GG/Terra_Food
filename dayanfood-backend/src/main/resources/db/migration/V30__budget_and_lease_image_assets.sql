ALTER TABLE image_asset
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN byte_size BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN worker_owner VARCHAR(36) NULL,
    ADD COLUMN lease_until TIMESTAMP NULL,
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD INDEX idx_image_owner_created (owner_user_id, created_at),
    ADD INDEX idx_image_lease (status, lease_until);

-- A locking row serializes quota reservations across application instances.
CREATE TABLE image_upload_budget_guard (id INT PRIMARY KEY);
INSERT INTO image_upload_budget_guard (id) VALUES (1);
