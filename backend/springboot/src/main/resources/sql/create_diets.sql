-- 주의: ddl-auto가 validate이므로 이 DDL을 RDS에 직접 실행 후 서버 시작할 것
-- 참고: user_id는 DynamoDB User.id(UUID 문자열)를 참조하므로 VARCHAR(36) 사용.
--       users 테이블이 RDS에 없으므로 FOREIGN KEY 제약은 적용하지 않음.

CREATE TABLE diets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    meal_type     VARCHAR(20)  NOT NULL,
    food_name     VARCHAR(255) NOT NULL,
    calories      DOUBLE,
    carbohydrate  DOUBLE,
    protein       DOUBLE,
    total_fat     DOUBLE,
    sugar         DOUBLE,
    dietary_fiber DOUBLE,
    sodium        DOUBLE,
    meal_time     DATETIME     NOT NULL,
    data_source   VARCHAR(20)  NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    UNIQUE KEY uq_diet_user_time_food (user_id, meal_time, food_name)
);
