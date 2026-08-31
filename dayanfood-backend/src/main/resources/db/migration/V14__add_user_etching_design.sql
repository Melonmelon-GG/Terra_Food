CREATE TABLE user_etching_design (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    layer_one_json JSON NOT NULL,
    layer_two_json JSON NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_etching_design_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    INDEX idx_user_etching_owner (user_id, updated_at),
    INDEX idx_user_etching_selected (user_id, selected)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
