-- =============================================
-- 체크당 (checkdang) ERD SQL
-- RDS 테이블 + DynamoDB 테이블 (참고용)
-- =============================================

-- =============================================
-- RDS 테이블
-- =============================================

CREATE TABLE `user` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`email`	VARCHAR(255)	NOT NULL	COMMENT '이메일',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '이름',
	`provider`	VARCHAR(50)	NULL	COMMENT '로그인제공자 (GOOGLE/KAKAO)',
	`age`	INT	NULL	COMMENT '나이',
	`gender`	VARCHAR(10)	NULL	COMMENT '성별 (MALE/FEMALE)',
	`height`	DECIMAL(5, 2)	NULL	COMMENT '키 (cm)',
	`weight`	DECIMAL(5, 2)	NULL	COMMENT '몸무게 (kg)',
	`diabetes_type`	VARCHAR(20)	NULL	COMMENT '당뇨유형 (1형/2형/임신성)',
	`terms_agreed`	TINYINT(1)	NOT NULL	DEFAULT 0	COMMENT '이용약관동의',
	`privacy_agreed`	TINYINT(1)	NOT NULL	DEFAULT 0	COMMENT '개인정보동의',
	`marketing_agreed`	TINYINT(1)	NULL	DEFAULT 0	COMMENT '마케팅동의',
	`terms_agreed_at`	DATETIME	NULL	COMMENT '약관동의일시',
	`created_at`	DATETIME	NOT NULL	COMMENT '생성일시'
);

CREATE TABLE `sleep_data` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`sleep_time`	DATETIME	NOT NULL	COMMENT '수면시작시각',
	`wake_time`	DATETIME	NOT NULL	COMMENT '기상시각',
	`duration`	INT	NOT NULL	COMMENT '수면시간',
	`quality`	INT	NOT NULL	COMMENT '수면질 (1~5)',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디',
	`source_id`	VARCHAR(255)	NOT NULL	COMMENT '삼성헬스 원본 ID'
);

CREATE TABLE `diet` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`food_name`	VARCHAR(255)	NOT NULL	COMMENT '음식명',
	`image_url`	VARCHAR(500)	NULL	COMMENT '이미지URL',
	`recorded_at`	DATETIME	NOT NULL	COMMENT '기록일시',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디',
	`ai_result`	TEXT	NULL	COMMENT 'AI분석결과',
	`ai_recommendation`	TEXT	NULL	COMMENT 'AI추천',
	`carbohydrate`	DECIMAL(6, 2)	NULL	COMMENT '탄수화물 (g)',
	`calories`	INT	NULL	COMMENT '칼로리',
	`meal_time`	VARCHAR(20)	NULL	COMMENT '식사시간대 (아침/점심/저녁/간식)',
	`source_id`	VARCHAR(255)	NOT NULL	COMMENT '삼성헬스 원본 ID'
);

CREATE TABLE `exercise_record` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`exercise_name`	VARCHAR(255)	NOT NULL	COMMENT '운동명',
	`duration`	INT	NULL	COMMENT '운동시간 (분)',
	`recorded_at`	DATETIME	NOT NULL	COMMENT '기록일시',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디',
	`ai_result`	TEXT	NULL	COMMENT 'AI분석결과',
	`ai_recommendation`	TEXT	NULL	COMMENT 'AI추천',
	`source_id`	VARCHAR(255)	NOT NULL	COMMENT '삼성헬스 원본 ID',
	`sets`	INT	NULL	COMMENT '세트 수',
	`reps`	INT	NULL	COMMENT '반복 횟수',
	`weight_kg`	DECIMAL(5,2)	NULL	COMMENT '무게 (kg)'
);

CREATE TABLE `blood_glucose_prediction` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`predicted_level`	INT	NOT NULL	COMMENT '예측혈당수치',
	`predicted_at`	DATETIME	NOT NULL	COMMENT '예측일시',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디'
);

CREATE TABLE `insulin_record` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`insulin_type`	VARCHAR(100)	NOT NULL	COMMENT '인슐린종류 (속효성/지속성/혼합형)',
	`dosage`	DECIMAL(5, 2)	NOT NULL	COMMENT '투여량 (units)',
	`injected_at`	DATETIME	NOT NULL	COMMENT '투여일시',
	`memo`	TEXT	NULL	COMMENT '메모',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디'
);

CREATE TABLE `pain_record` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디',
	`muscle_name`	VARCHAR(50)	NOT NULL	COMMENT '근육명',
	`pain_type`	VARCHAR(50)	NOT NULL	COMMENT '통증종류',
	`pain_intensity`	TINYINT	NOT NULL	COMMENT '통증강도 (1~10)',
	`ai_cause`	TEXT	NULL	COMMENT 'AI분석원인',
	`ai_first_aid`	TEXT	NULL	COMMENT 'AI응급조치',
	`recorded_at`	DATETIME	NOT NULL	COMMENT '기록일시',
	`created_at`	DATETIME	NOT NULL	COMMENT '생성일시'
);

CREATE TABLE `subscription` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디',
	`payment_method`	VARCHAR(20)	NOT NULL	COMMENT '결제수단 (GOOGLE_PLAY/KAKAO_PAY)',
	`started_at`	DATETIME	NOT NULL	COMMENT '구독시작일',
	`expired_at`	DATETIME	NOT NULL	COMMENT '구독만료일',
	`status`	VARCHAR(20)	NOT NULL	COMMENT '구독상태 (ACTIVE/EXPIRED/CANCELLED)',
	`created_at`	DATETIME	NOT NULL	COMMENT '생성일시'
);

CREATE TABLE `family` (
	`id`	BIGINT	NOT NULL	COMMENT '아이디',
	`user_id`	BIGINT	NOT NULL	COMMENT '사용자아이디',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '이름',
	`phone`	VARCHAR(20)	NOT NULL	COMMENT '연락처',
	`relation`	VARCHAR(20)	NOT NULL	COMMENT '관계 (부모/자녀/배우자/기타)',
	`created_at`	DATETIME	NOT NULL	COMMENT '생성일시'
);

-- PK 설정
ALTER TABLE `user` ADD CONSTRAINT `PK_USER` PRIMARY KEY (`id`);
ALTER TABLE `sleep_data` ADD CONSTRAINT `PK_SLEEP_DATA` PRIMARY KEY (`id`);
ALTER TABLE `diet` ADD CONSTRAINT `PK_DIET` PRIMARY KEY (`id`);
ALTER TABLE `exercise_record` ADD CONSTRAINT `PK_EXERCISE_RECORD` PRIMARY KEY (`id`);
ALTER TABLE `blood_glucose_prediction` ADD CONSTRAINT `PK_BLOOD_GLUCOSE_PREDICTION` PRIMARY KEY (`id`);
ALTER TABLE `insulin_record` ADD CONSTRAINT `PK_INSULIN_RECORD` PRIMARY KEY (`id`);
ALTER TABLE `pain_record` ADD CONSTRAINT `PK_PAIN_RECORD` PRIMARY KEY (`id`);
ALTER TABLE `subscription` ADD CONSTRAINT `PK_SUBSCRIPTION` PRIMARY KEY (`id`);
ALTER TABLE `family` ADD CONSTRAINT `PK_FAMILY` PRIMARY KEY (`id`);

-- FK 설정
ALTER TABLE `sleep_data` ADD CONSTRAINT `FK_sleep_data_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `diet` ADD CONSTRAINT `FK_diet_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `exercise_record` ADD CONSTRAINT `FK_exercise_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `blood_glucose_prediction` ADD CONSTRAINT `FK_blood_glucose_prediction_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `insulin_record` ADD CONSTRAINT `FK_insulin_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `pain_record` ADD CONSTRAINT `FK_pain_record_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `subscription` ADD CONSTRAINT `FK_subscription_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);
ALTER TABLE `family` ADD CONSTRAINT `FK_family_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`);

-- =============================================
-- DynamoDB 테이블 (참고용 - 실제 SQL 아님)
-- PK: user_date, SK: timestamp
-- =============================================

CREATE TABLE `heart_rate` (
	`user_date`	VARCHAR(100)	NOT NULL	COMMENT '사용자날짜 (user_id + date)',
	`timestamp`	VARCHAR(50)	NOT NULL	COMMENT '타임스탬프',
	`bpm`	INT	NOT NULL	COMMENT '심박수',
	`ibi`	DECIMAL(10, 4)	NULL	COMMENT 'IBI',
	`source_id`	VARCHAR(255)	NOT NULL	COMMENT '삼성헬스 원본 ID'
);

CREATE TABLE `step_calorie` (
	`user_date`	VARCHAR(100)	NOT NULL	COMMENT '사용자날짜 (user_id + date)',
	`timestamp`	VARCHAR(50)	NOT NULL	COMMENT '타임스탬프',
	`step_count`	INT	NOT NULL	COMMENT '걸음수',
	`calorie`	DECIMAL(10, 2)	NOT NULL	COMMENT '소비칼로리',
	`source_id`	VARCHAR(255)	NOT NULL	COMMENT '삼성헬스 원본 ID'
);

CREATE TABLE `blood_glucose_record` (
	`user_date`	VARCHAR(100)	NOT NULL	COMMENT '사용자날짜 (user_id + date)',
	`timestamp`	VARCHAR(50)	NOT NULL	COMMENT '타임스탬프',
	`level`	INT	NOT NULL	COMMENT '혈당수치',
	`meal_timing`	VARCHAR(20)	NOT NULL	COMMENT '식사타이밍 (식전/식후/공복)',
	`memo`	VARCHAR(255)	NULL	COMMENT '메모'
);

ALTER TABLE `heart_rate` ADD CONSTRAINT `PK_HEART_RATE` PRIMARY KEY (`user_date`);
ALTER TABLE `step_calorie` ADD CONSTRAINT `PK_STEP_CALORIE` PRIMARY KEY (`user_date`);
ALTER TABLE `blood_glucose_record` ADD CONSTRAINT `PK_BLOOD_GLUCOSE_RECORD` PRIMARY KEY (`user_date`);
