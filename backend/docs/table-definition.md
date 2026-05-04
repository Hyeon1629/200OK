# 체크당 (checkdang) 테이블 정의서

---

## RDS 테이블

### user
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 이메일 | email | VARCHAR(255) | NOT NULL | |
| 이름 | name | VARCHAR(100) | NOT NULL | |
| 로그인제공자 | provider | VARCHAR(50) | NULL | |
| 나이 | age | INT | NULL | |
| 성별 | gender | VARCHAR(10) | NULL | |
| 키 | height | DECIMAL(5,2) | NULL | |
| 몸무게 | weight | DECIMAL(5,2) | NULL | |
| 당뇨유형 | diabetes_type | VARCHAR(20) | NULL | |
| 이용약관동의 | terms_agreed | TINYINT(1) | NOT NULL | |
| 개인정보동의 | privacy_agreed | TINYINT(1) | NOT NULL | |
| 마케팅동의 | marketing_agreed | TINYINT(1) | NULL | |
| 약관동의일시 | terms_agreed_at | DATETIME | NULL | |
| 생성일시 | created_at | DATETIME | NOT NULL | |

---

### sleep_data
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 수면시작시각 | sleep_time | DATETIME | NOT NULL | |
| 기상시각 | wake_time | DATETIME | NOT NULL | |
| 수면시간 | duration | INT | NOT NULL | |
| 수면질 | quality | INT | NOT NULL | |
| 삼성헬스원본ID | source_id | VARCHAR(255) | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### diet
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 음식명 | food_name | VARCHAR(255) | NOT NULL | |
| 이미지URL | image_url | VARCHAR(500) | NULL | |
| 기록일시 | recorded_at | DATETIME | NOT NULL | |
| AI분석결과 | ai_result | TEXT | NULL | |
| AI추천 | ai_recommendation | TEXT | NULL | |
| 탄수화물 | carbohydrate | DECIMAL(6,2) | NULL | |
| 칼로리 | calories | INT | NULL | |
| 식사시간대 | meal_time | VARCHAR(20) | NULL | |
| 삼성헬스원본ID | source_id | VARCHAR(255) | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### exercise_record
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 운동명 | exercise_name | VARCHAR(255) | NOT NULL | |
| 운동시간 | duration | INT | NULL | |
| 기록일시 | recorded_at | DATETIME | NOT NULL | |
| AI분석결과 | ai_result | TEXT | NULL | |
| AI추천 | ai_recommendation | TEXT | NULL | |
| 세트수 | sets | INT | NULL | |
| 반복횟수 | reps | INT | NULL | |
| 무게 | weight_kg | DECIMAL(5,2) | NULL | |
| 삼성헬스원본ID | source_id | VARCHAR(255) | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### blood_glucose_prediction
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 예측혈당수치 | predicted_level | INT | NOT NULL | |
| 예측일시 | predicted_at | DATETIME | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### insulin_record
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 인슐린종류 | insulin_type | VARCHAR(100) | NOT NULL | |
| 투여량 | dosage | DECIMAL(5,2) | NOT NULL | |
| 투여일시 | injected_at | DATETIME | NOT NULL | |
| 메모 | memo | TEXT | NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### pain_record
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 근육명 | muscle_name | VARCHAR(50) | NOT NULL | |
| 통증종류 | pain_type | VARCHAR(50) | NOT NULL | |
| 통증강도 | pain_intensity | TINYINT | NOT NULL | |
| AI분석원인 | ai_cause | TEXT | NULL | |
| AI응급조치 | ai_first_aid | TEXT | NULL | |
| 기록일시 | recorded_at | DATETIME | NOT NULL | |
| 생성일시 | created_at | DATETIME | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### subscription
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 결제수단 | payment_method | VARCHAR(20) | NOT NULL | |
| 구독시작일 | started_at | DATETIME | NOT NULL | |
| 구독만료일 | expired_at | DATETIME | NOT NULL | |
| 구독상태 | status | VARCHAR(20) | NOT NULL | |
| 생성일시 | created_at | DATETIME | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

### family
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 아이디 | id | BIGINT | NOT NULL | PK |
| 이름 | name | VARCHAR(100) | NOT NULL | |
| 연락처 | phone | VARCHAR(20) | NOT NULL | |
| 관계 | relation | VARCHAR(20) | NOT NULL | |
| 생성일시 | created_at | DATETIME | NOT NULL | |
| 사용자아이디 | user_id | BIGINT | NOT NULL | FK |

---

## DynamoDB 테이블

### heart_rate
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 사용자날짜 | user_date | VARCHAR(100) | NOT NULL | PK |
| 타임스탬프 | timestamp | VARCHAR(50) | NOT NULL | SK |
| 심박수 | bpm | INT | NOT NULL | |
| 기기아이디 | device_id | VARCHAR(100) | NOT NULL | |
| IBI | ibi | DECIMAL(10,4) | NULL | |
| 삼성헬스원본ID | source_id | VARCHAR(255) | NOT NULL | |

---

### step_calorie
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 사용자날짜 | user_date | VARCHAR(100) | NOT NULL | PK |
| 타임스탬프 | timestamp | VARCHAR(50) | NOT NULL | SK |
| 걸음수 | step_count | INT | NOT NULL | |
| 소비칼로리 | calorie | DECIMAL(10,2) | NOT NULL | |
| 기기아이디 | device_id | VARCHAR(100) | NOT NULL | |
| 삼성헬스원본ID | source_id | VARCHAR(255) | NOT NULL | |

---

### blood_glucose_record
| Logical Name | Physical Name | Type | Null | Key |
|---|---|---|---|---|
| 사용자날짜 | user_date | VARCHAR(100) | NOT NULL | PK |
| 타임스탬프 | timestamp | VARCHAR(50) | NOT NULL | SK |
| 혈당수치 | level | INT | NOT NULL | |
| 식사타이밍 | meal_timing | VARCHAR(20) | NOT NULL | |
| 메모 | memo | VARCHAR(255) | NULL | |
