CREATE TABLE food_like (
    food_id    BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (food_id, user_id),

    CONSTRAINT fk_food_like_food
        FOREIGN KEY (food_id) REFERENCES food (id) ON DELETE CASCADE,
    CONSTRAINT fk_food_like_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,

    INDEX idx_food_like_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;