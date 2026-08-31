CREATE TABLE user_review_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    field VARCHAR(30) NOT NULL,
    current_value VARCHAR(200) NOT NULL DEFAULT '',
    pending_value VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(50) NULL,
    reviewed_at DATETIME NULL,
    UNIQUE KEY uk_review_item_user_field (user_id, field),
    CONSTRAINT fk_review_item_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 将存量签名待审记录回填到通用待审表，随后旧三列不再表达待审状态（防双轨）。
INSERT INTO user_review_item (user_id, field, current_value, pending_value, status)
SELECT id, 'SIGNATURE', COALESCE(signature, ''), signature_pending, 'PENDING'
FROM app_user
WHERE signature_status = 'PENDING';

UPDATE app_user SET signature_status = 'APPROVED' WHERE signature_status = 'PENDING';