CREATE TABLE achievement (
    id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(50)   NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(500)  NOT NULL,
    image_url   VARCHAR(500)  NOT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_achievement_code UNIQUE (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_achievement (
    user_id       BIGINT   NOT NULL,
    achievement_id BIGINT  NOT NULL,
    unlocked_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notified_at   DATETIME NULL,
    selected      BOOLEAN  NOT NULL DEFAULT FALSE,

    PRIMARY KEY (user_id, achievement_id),
    CONSTRAINT fk_user_achievement_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_achievement_achievement
        FOREIGN KEY (achievement_id) REFERENCES achievement (id) ON DELETE CASCADE,
    INDEX idx_user_achievement_notification (user_id, notified_at),
    INDEX idx_user_achievement_selected (user_id, selected)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO achievement (code, name, description, image_url)
VALUES (
    'FIRST_LOGIN',
    '初入炎境',
    '首次登录大炎珍馐志，踏上寻访九州风味的旅程。',
    '/achievements/first-login.png'
);
