## 1. 혈당 예측 (blood_glucose_prediction)

### 입력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 음식명 | `diet` | `food_name` |
| 탄수화물 | `diet` | `carbohydrate` |
| 칼로리 | `diet` | `calories` |
| 식사시간대 | `diet` | `meal_time` |
| 식사기록일시 | `diet` | `recorded_at` |
| 수면시작시각 | `sleep_data` | `sleep_time` |
| 기상시각 | `sleep_data` | `wake_time` |
| 수면시간 | `sleep_data` | `duration` |
| 수면질 | `sleep_data` | `quality` |
| 운동명 | `exercise_record` | `exercise_name` |
| 운동시간 | `exercise_record` | `duration` |
| 운동기록일시 | `exercise_record` | `recorded_at` |
| 심박수 | `heart_rate` | `bpm` |
| 심박수측정시각 | `heart_rate` | `timestamp` |
| 걸음수 | `step_calorie` | `step_count` |
| 소비칼로리 | `step_calorie` | `calorie` |
| 걸음수측정시각 | `step_calorie` | `timestamp` |
| 혈당수치 | `blood_glucose_record` | `level` |
| 식사타이밍 | `blood_glucose_record` | `meal_timing` |
| 혈당측정시각 | `blood_glucose_record` | `timestamp` |

### 출력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 예측혈당수치 | `blood_glucose_prediction` | `predicted_level` |
| 예측일시 | `blood_glucose_prediction` | `predicted_at` |

---

## 2. 통증 분석 (pain_record)

### 입력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 근육명 (현재) | `pain_record` | `muscle_name` |
| 통증종류 (현재) | `pain_record` | `pain_type` |
| 통증강도 (현재) | `pain_record` | `pain_intensity` |
| 과거 동일부위 근육명 | `pain_record` | `muscle_name` (WHERE muscle_name = 현재근육명) |
| 과거 동일부위 통증종류 | `pain_record` | `pain_type` |
| 과거 동일부위 통증강도 | `pain_record` | `pain_intensity` |
| 과거 동일부위 기록일시 | `pain_record` | `recorded_at` |
| 최근 식단 | `diet` | `food_name`, `calories`, `meal_time` |
| 최근 수면 | `sleep_data` | `duration`, `quality` |
| 최근 운동 | `exercise_record` | `exercise_name`, `duration` |

### 출력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| AI 분석 원인 | `pain_record` | `ai_cause` |
| AI 응급조치 | `pain_record` | `ai_first_aid` |

---

## 3. 식단 분석 (diet)

### 입력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 음식명 | `diet` | `food_name` |
| 탄수화물 | `diet` | `carbohydrate` |
| 칼로리 | `diet` | `calories` |
| 식사시간대 | `diet` | `meal_time` |

### 출력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| AI 분석 결과 | `diet` | `ai_result` |
| AI 추천 | `diet` | `ai_recommendation` |

---

## 4. 운동 분석 (exercise_record)

### 입력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 운동명 | `exercise_record` | `exercise_name` |
| 운동시간 | `exercise_record` | `duration` |
| 기록일시 | `exercise_record` | `recorded_at` |

### 출력 데이터 경로
| 데이터 | 테이블 | 필드 |
|---|---|---|
| AI 분석 결과 | `exercise_record` | `ai_result` |
| AI 추천 | `exercise_record` | `ai_recommendation` |

---

## 5. 사용자 정보 (user)

### 회원가입
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 이메일 | `user` | `email` |
| 이름 | `user` | `name` |
| 로그인제공자 | `user` | `provider` |
| 이용약관동의 | `user` | `terms_agreed` |
| 개인정보동의 | `user` | `privacy_agreed` |
| 마케팅동의 | `user` | `marketing_agreed` |
| 약관동의일시 | `user` | `terms_agreed_at` |
| 생성일시 | `user` | `created_at` |

### 프로필 수정
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 나이 | `user` | `age` |
| 성별 | `user` | `gender` |
| 키 | `user` | `height` |
| 몸무게 | `user` | `weight` |
| 당뇨유형 | `user` | `diabetes_type` |

### 프로필 조회
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 전체 사용자 정보 | `user` | 전체 필드 (WHERE id = 로그인유저) |

---

## 6. 가족 정보 (family)

### 가족 등록
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 사용자아이디 | `family` | `user_id` |
| 이름 | `family` | `name` |
| 연락처 | `family` | `phone` |
| 관계 | `family` | `relation` |
| 생성일시 | `family` | `created_at` |

### 가족 조회
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 등록된 가족 목록 | `family` | 전체 필드 (WHERE user_id = 로그인유저) |

### 알림 발송 시
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 가족 연락처 | `family` | `phone` |
| 가족 이름 | `family` | `name` |

---

## 7. 구독/결제 정보 (subscription)

### 구독 시작
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 사용자아이디 | `subscription` | `user_id` |
| 결제수단 | `subscription` | `payment_method` |
| 구독시작일 | `subscription` | `started_at` |
| 구독만료일 | `subscription` | `expired_at` |
| 구독상태 | `subscription` | `status` (ACTIVE) |
| 생성일시 | `subscription` | `created_at` |

### 구독 만료/취소
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 구독상태 | `subscription` | `status` (EXPIRED / CANCELLED) |

### 구독 조회
| 데이터 | 테이블 | 필드 |
|---|---|---|
| 현재 구독 정보 | `subscription` | 전체 필드 (WHERE user_id = 로그인유저) |

### 프리미엄 여부 확인
| 데이터 | 출처 | 필드 |
|---|---|---|
| 프리미엄 여부 | JWT 토큰 (Cognito) | `custom:is_premium` |