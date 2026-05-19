-- 주의: ddl-auto가 validate이므로 이 DDL을 RDS에 직접 실행 후 서버 시작할 것
-- 참고: user_id는 DynamoDB User.id(UUID 문자열)를 참조하므로 VARCHAR(36) 사용.
--       users 테이블이 RDS에 없으므로 FOREIGN KEY(user_id) 제약은 적용하지 않음.

CREATE TABLE sleeps (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    sleep_start   DATETIME     NOT NULL,
    sleep_end     DATETIME     NOT NULL,
    total_minutes BIGINT       NOT NULL,
    efficiency    DOUBLE,
    data_source   VARCHAR(20)  NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    UNIQUE KEY uq_sleep_user_start (user_id, sleep_start)
);

CREATE TABLE sleep_stages (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    sleep_id         BIGINT      NOT NULL,
    stage_type       VARCHAR(20) NOT NULL,
    start_time       DATETIME    NOT NULL,
    end_time         DATETIME    NOT NULL,
    duration_minutes BIGINT      NOT NULL,
    FOREIGN KEY (sleep_id) REFERENCES sleeps(id)
);
