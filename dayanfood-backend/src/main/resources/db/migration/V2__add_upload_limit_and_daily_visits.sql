ALTER TABLE app_user
    ADD COLUMN last_food_upload_at DATETIME NULL AFTER active;

UPDATE app_user u
LEFT JOIN (
    SELECT created_by, MAX(created_at) AS latest_upload_at
    FROM food
    GROUP BY created_by
) uploads ON uploads.created_by = u.username
SET u.last_food_upload_at = uploads.latest_upload_at
WHERE uploads.latest_upload_at IS NOT NULL;

CREATE TABLE food_daily_visit (
    food_id    BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    visit_date DATE     NOT NULL,
    visited_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (food_id, user_id, visit_date),
    CONSTRAINT fk_food_daily_visit_food
        FOREIGN KEY (food_id) REFERENCES food (id) ON DELETE CASCADE,
    CONSTRAINT fk_food_daily_visit_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    INDEX idx_food_daily_visit_date (visit_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
