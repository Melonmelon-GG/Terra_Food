CREATE TABLE food_comment (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    food_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    content    VARCHAR(500) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_food_comment_food
        FOREIGN KEY (food_id) REFERENCES food (id) ON DELETE CASCADE,
    CONSTRAINT fk_food_comment_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,

    INDEX idx_food_comment_food_created (food_id, created_at, id),
    INDEX idx_food_comment_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
