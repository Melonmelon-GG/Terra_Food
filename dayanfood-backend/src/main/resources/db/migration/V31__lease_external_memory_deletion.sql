ALTER TABLE external_identity_deletion
    ADD COLUMN worker_owner CHAR(36) NULL,
    ADD COLUMN lease_until TIMESTAMP NULL,
    ADD INDEX idx_external_delete_lease (status, lease_until);
