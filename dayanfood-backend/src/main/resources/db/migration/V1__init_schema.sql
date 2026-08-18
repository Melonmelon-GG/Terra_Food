CREATE TABLE region (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL UNIQUE,
    province    VARCHAR(50) NOT NULL,
    description VARCHAR(500)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE food (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    region_id   BIGINT        NOT NULL,
    summary     VARCHAR(1000) NOT NULL,
    story       TEXT          NOT NULL,
    ingredients VARCHAR(500)  NOT NULL,
    image_url   VARCHAR(500),
    heat        INT           NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_food_region
        FOREIGN KEY (region_id) REFERENCES region (id),

    INDEX idx_food_region (region_id),
    INDEX idx_food_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
