-- Pre-release baseline: schema and initial reference data live in one migration.
CREATE TABLE region (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL,
    province    VARCHAR(50) NOT NULL,
    description VARCHAR(500),

    CONSTRAINT uk_region_province_name UNIQUE (province, name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE food (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    region_id   BIGINT        NOT NULL,
    latitude    DECIMAL(10, 7),
    longitude   DECIMAL(10, 7),
    address     VARCHAR(500),
    summary     VARCHAR(1000) NOT NULL,
    story       TEXT          NOT NULL,
    ingredients VARCHAR(500)  NOT NULL,
    image_url   VARCHAR(500),
    heat        INT           NOT NULL DEFAULT 0,
    created_by  VARCHAR(50)    NOT NULL DEFAULT '无名',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_food_region
        FOREIGN KEY (region_id) REFERENCES region (id),

    INDEX idx_food_region (region_id),
    INDEX idx_food_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE app_user (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password     VARCHAR(100) NOT NULL,
    display_name VARCHAR(50)  NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_app_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO region (name, province, description)
VALUES ('成都', '四川', '天府之国，川菜与市井小吃交相辉映。'),
       ('潮汕', '广东', '山海相拥，讲究本味与时鲜。'),
       ('苏州', '江苏', '江南水乡，滋味清雅而不失丰润。'),
       ('西安', '陕西', '古都长安，面食与胡汉风味汇聚。');

INSERT INTO food (
    name,
    region_id,
    latitude,
    longitude,
    summary,
    story,
    ingredients,
    image_url,
    heat
)
VALUES ('麻婆豆腐', 1, 30.5728000, 104.0668000,
        '麻、辣、烫、香、酥、嫩、鲜、活，一碗烟火气十足的川味经典。',
        '相传始于清同治年间成都万福桥边的小饭铺，因店主陈氏脸有麻点而得名。',
        '豆腐、牛肉末、郫县豆瓣、花椒、蒜苗',
        'https://images.unsplash.com/photo-1582450871972-ab5ca641643d?auto=format&fit=crop&w=1200&q=80', 98),
       ('潮汕牛肉火锅', 2, 23.3541000, 116.6820000,
        '清汤见真章，鲜切牛肉按部位涮出不同口感。',
        '从街巷牛肉摊发展而来，刀工、部位和秒数共同定义一口鲜嫩。',
        '黄牛肉、牛骨清汤、南姜、沙茶酱、芹菜',
        'https://images.unsplash.com/photo-1563245372-f21724e3856d?auto=format&fit=crop&w=1200&q=80', 92),
       ('松鼠桂鱼', 3, 31.2989000, 120.5853000,
        '形似松鼠、外脆里嫩，以酸甜卤汁唤醒江南宴席。',
        '精细花刀体现苏帮菜功夫，乾隆下江南的传说又为它添了传奇。',
        '桂鱼、番茄酱、松子、米醋、白糖',
        'https://images.unsplash.com/photo-1515003197210-e0cd71810b5f?auto=format&fit=crop&w=1200&q=80', 86),
       ('肉夹馍', 4, 34.3416000, 108.9398000,
        '腊汁肉酥烂醇香，白吉馍外酥里软。',
        '肉夹于馍的古汉语表达流传至今，是关中人日常而扎实的一餐。',
        '猪肉、白吉馍、桂皮、八角、冰糖',
        'https://images.unsplash.com/photo-1565299507177-b0ac66763828?auto=format&fit=crop&w=1200&q=80', 89);
