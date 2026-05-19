-- users 테이블 DDL
-- User를 DynamoDB에서 RDS로 전환함에 따라 생성
-- ddl-auto: none 이므로 RDS에 직접 실행 후 서버 시작할 것

CREATE TABLE users (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    email                    VARCHAR(255) NOT NULL UNIQUE,
    password                 VARCHAR(255),                          -- OAuth2 사용자는 NULL
    name                     VARCHAR(100) NOT NULL,
    role                     VARCHAR(20)  NOT NULL DEFAULT 'PATIENT',
    provider                 VARCHAR(20),
    provider_id              VARCHAR(255),
    is_guest                 TINYINT(1)   DEFAULT 0,
    terms_agreed             TINYINT(1)   DEFAULT 0,
    gender                   VARCHAR(10),
    birth_date               VARCHAR(10),                           -- YYYY-MM-DD
    height                   INT,
    weight                   INT,
    diabetes_type            VARCHAR(20),
    is_premium               TINYINT(1)   DEFAULT 0,
    premium_expires_at       DATETIME,
    family_group_id          VARCHAR(100),
    family_role              VARCHAR(20),
    target_blood_sugar       INT,
    samsung_health_connected TINYINT(1)   DEFAULT 0,
    notification_enabled     TINYINT(1)   DEFAULT 1,
    account_status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at               DATETIME     NOT NULL,
    updated_at               DATETIME,
    INDEX idx_users_email (email)
);
