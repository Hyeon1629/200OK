# 체크당 (checkdang) 프로젝트 백엔드 문서

> 본 문서는 체크당 백엔드의 **서비스 설계, 인프라, 배포 절차**를 한 곳에 정리한 통합 문서이다.

---

## 1. 서비스 개요

당뇨 환자를 위한 혈당 관리 앱. 삼성헬스 데이터 연동, AI 기반 혈당 예측, 식단·운동·통증 분석을 제공한다.

---

## 2. 기술 스택 및 인프라

| 분류 | 기술 |
|---|---|
| 클라이언트 | Android + Samsung Health SDK |
| 소셜 로그인 | Google, KakaoTalk (OAuth2) |
| DNS | Amazon Route 53 (`api.checkdang.xyz`) |
| 인증 | AWS Cognito (User Pool: `ap-northeast-2_dB7hAykk4`) — 현재 자체 JWT 운영, Cognito로 마이그레이션 예정 |
| TLS | ACM 와일드카드 인증서 (`*.checkdang.xyz`) |
| Load Balancer | AWS ALB (`checkdang-alb`) — path-based 라우팅 |
| Spring Boot | AWS Fargate (`chekdang-spring-task-service`, Task Def v4) |
| FastAPI | AWS EC2 (`i-0c0f422cc734ff6ff`, `10.0.136.22`, t3.small) |
| AI 모델 (혈당 예측) | 머신러닝 모델 (FastAPI 내부, 추후 구현) |
| AI 모델 (분석) | Gemini API (`gemini-2.5-flash`) |
| RDS | Amazon RDS (MySQL 8.0, Private) |
| NoSQL | Amazon DynamoDB |
| 스토리지 | Amazon S3 |
| 컨테이너 레지스트리 | Amazon ECR (`chekdang-spring-boot`, `chekdang-fastapi`) |
| 비밀 관리 | AWS Secrets Manager (`checkdang/springboot`) |
| 원격 접속 | AWS Systems Manager Session Manager (SSH 키 미사용) |
| CI/CD | GitHub Actions (계획) — 현재는 수동 배포 |
| 결제 | Google Play Billing Library / KakaoPay |

### 전체 요청 흐름 (현재)

```
Android 앱
    ↓ HTTPS (api.checkdang.xyz)
Route 53
    ↓
ALB (path-based 라우팅)
    ├── /heart-rate/*, /step-calorie/*, /analyze/* → FastAPI (EC2)
    └── 그 외                                       → Spring Boot (Fargate)
```

### 진행 현황

#### 완료
- ✅ Route 53 도메인 연결 (`api.checkdang.xyz`)
- ✅ ACM 와일드카드 인증서 (`*.checkdang.xyz`) 적용 — HTTPS 강제
- ✅ ALB 구성 + path-based 라우팅 (Spring Boot ↔ FastAPI 분기)
- ✅ RDS 구축 + Private 격리 (PubliclyAccessible=false)
- ✅ RDS 스키마 검증 (`diet`, `sleep_data`, `sleep_stages`, `exercise_record`, `insulin_record`)
- ✅ S3 구축
- ✅ DynamoDB 테이블 생성: `heart_rate`, `step_calorie`, `blood_glucose_record`
- ✅ ECR 저장소 (Spring Boot, FastAPI)
- ✅ Spring Boot Fargate 배포 (수동, Task Def v4)
- ✅ FastAPI EC2 배포 + Secrets Manager 통합 + 영구 재배포 스크립트
- ✅ FastAPI 엔드포인트 검증: `/heart-rate`, `/step-calorie`, `/analyze/diet`, `/analyze/health-report`

#### 진행 예정
- 📌 `users` RDS 마이그레이션 (현재 DynamoDB 잘못 저장)
- 📌 자체 JWT → Cognito 마이그레이션
- 📌 FastAPI 인증 추가 (현재 미보호)
- 📌 혈당 처리 Spring Boot → FastAPI 이전
- 📌 AI 결과 저장 로직 (`Diet.ai_result`, `Exercise.ai_result` 미구현)
- 📌 API Gateway + Cognito Authorizer
- 📌 WAF 적용
- 📌 ALB Internal 전환
- 📌 GitHub Actions CI/CD

---

## 3. 데이터 아키텍처

### RDS (MySQL 8) — Spring Boot + JPA

| 테이블 | 설명 | 입력 방식 | 상태 |
|---|---|---|---|
| `user` | 사용자 계정/프로필 | 회원가입 / OAuth2 | ⚠️ 현재 DynamoDB에 잘못 저장 중 |
| `diet` | 식단 + AI 분석 결과 | 삼성헬스 | ✅ 스키마 OK, 데이터 0건 |
| `sleep_data` | 수면 기록 | 삼성헬스 | ✅ |
| `sleep_stages` | 수면 단계 세부 | 삼성헬스 | ✅ |
| `exercise_record` | 운동 + AI 분석 결과 | 삼성헬스 | ✅ |
| `insulin_record` | 인슐린 투여 기록 | 직접 입력 | ✅ |
| `pain_record` | 통증 + AI 분석 결과 | 직접 입력 (2D 바디맵) | 📌 추가 예정 |
| `subscription` | 구독/결제 정보 | Google Play / KakaoPay | 📌 추가 예정 |
| `blood_glucose_prediction` | AI 혈당 예측 결과 | FastAPI 자동 저장 | 📌 추가 예정 |
| `family` | 가족 연락처 | 직접 입력 | 📌 추가 예정 |

### DynamoDB — 시계열 데이터 (PK: `user_date`, SK: `timestamp`)

| 테이블 | 설명 | 담당 서버 | 상태 |
|---|---|---|---|
| `heart_rate` | 심박수 (bpm, ibi) | FastAPI | ✅ |
| `step_calorie` | 걸음수 + 소비칼로리 | FastAPI | ✅ |
| `blood_glucose_record` | 실측 혈당 수치 | FastAPI(예정), 현재는 Spring Boot | ✅ 테이블만 존재 |

---

## 4. 전체 기능 및 구현 방법

### 4-1. 회원/인증
- Google / KakaoTalk OAuth2 → Cognito 인증 (계획). 현재는 Spring Boot 자체 JWT
- JWT 토큰에 `custom:is_premium` 클레임으로 프리미엄 여부 포함 (계획)
- 사용자 정보 → RDS `user` 테이블 JPA 저장 (현재 DynamoDB 잘못 저장 중)

### 4-2. 삼성헬스 데이터 동기화
- Android에서 Samsung Health SDK 수집 → Spring Boot/FastAPI 전달
- 중복 방지: 각 테이블의 `source_id` 로 원본 ID 관리
- 저장 위치
  - Spring Boot → RDS: `diet`, `sleep_data`, `exercise_record`
  - FastAPI → DynamoDB: `heart_rate`, `step_calorie`

### 4-3. 혈당 기록
- 사용자가 혈당 수치 / 식사 타이밍 / 메모 입력
- FastAPI → DynamoDB `blood_glucose_record` 저장 (계획). 현재 Spring Boot가 처리.

### 4-4. AI 혈당 예측
- Spring Boot가 데이터 수집 → FastAPI 전달:

  | 소스 | 데이터 |
  |---|---|
  | RDS `diet` | 음식명, 탄수화물, 칼로리, 식사시간대 |
  | RDS `sleep_data` | 수면시작/기상, 시간, 질 |
  | RDS `exercise_record` | 운동명, 시간 |
  | DynamoDB `heart_rate` | 심박수, 측정시각 |
  | DynamoDB `step_calorie` | 걸음수, 칼로리 |
  | DynamoDB `blood_glucose_record` | 실측 혈당 |

- FastAPI 내부 머신러닝 모델로 예측 → RDS `blood_glucose_prediction` 저장 (모두 추후 구현)

### 4-5. AI 통증 분석
- 사용자 입력: 근육명, 통증종류, 강도(1~10)
- FastAPI → Gemini API → `ai_cause`, `ai_first_aid` → RDS `pain_record` 저장 (계획)

### 4-6. AI 식단 분석 (✅ 운영 중)
- 엔드포인트: `POST /analyze/diet`
- 입력: 음식명, 탄수화물, 칼로리, 식사시간대
- Gemini 호출 후 한국어 5문장 이내 분석 응답
- 저장 로직 미구현(`Diet.ai_result` 컬럼은 존재) — 추후 구현

### 4-7. AI 종합 헬스 리포트 (✅ 운영 중, 신규)
- 엔드포인트: `POST /analyze/health-report`
- 입력: user, 기간(from~to), diets, sleeps, exercises
- Gemini 호출 후 5섹션 마크다운 리포트 응답:
  `## Summary` / `## Diet Analysis` / `## Sleep Analysis` / `## Exercise Analysis` / `## Recommended Actions`
- 호출자: Spring Boot `AiAnalysisClient.analyzeHealthReport()`

### 4-8. AI 운동 분석
- 입력: 운동명, 시간, 세트/반복/무게 (삼성헬스)
- FastAPI → Gemini → `ai_result`, `ai_recommendation` → RDS `exercise_record` 업데이트 (미구현)

### 4-9. 인슐린 기록
- 사용자 직접 입력: 종류(속효성/지속성/혼합형), 투여량, 일시, 메모
- RDS `insulin_record` 저장

### 4-10. 가족 연동 / 알림 / 구독
- 가족 정보 → RDS `family` (계획)
- 결제: Google Play Billing / KakaoPay → RDS `subscription` (계획)
- 프리미엄 여부 → Cognito JWT `custom:is_premium` (계획)

---

## 5. FastAPI 배포 가이드

### 5-1. 인프라 개요

| 항목 | 값 |
|---|---|
| EC2 인스턴스 ID | `i-0c0f422cc734ff6ff` |
| Private IP | `10.0.136.22` |
| 컨테이너 이름 | `checkdang-fastapi` |
| ECR 저장소 | `353842237556.dkr.ecr.ap-northeast-2.amazonaws.com/chekdang-fastapi` |
| 노출 포트 | `8000` (ALB → 8000) |
| 외부 진입점 | `https://api.checkdang.xyz` |
| IAM Role | `checkdang-fastapi-ec2-role` |
| 영구 배포 스크립트 | `/usr/local/bin/deploy-fastapi.sh` |

### 5-2. 시크릿 관리

- AWS Secrets Manager: `checkdang/springboot` (Spring Boot와 공유)
- 표준 JSON 포맷: `{"KEY":"value", ...}`
- 컨테이너에 주입되는 키
  - `GEMINI_API_KEY`
  - `GEMINI_MODEL` (기본 `gemini-2.5-flash`)
- EC2 IAM Role에 inline policy `SecretsManagerRead`로 `secretsmanager:GetSecretValue` (`checkdang/springboot-*`) 허용

### 5-3. 재배포 절차 (코드 변경 후)

**Step 1 — 로컬에서 ECR 빌드 + 푸시**

PowerShell:
```powershell
cd backend\fastapi

aws ecr get-login-password --region ap-northeast-2 `
  | docker login --username AWS --password-stdin 353842237556.dkr.ecr.ap-northeast-2.amazonaws.com

$ECR = "353842237556.dkr.ecr.ap-northeast-2.amazonaws.com/chekdang-fastapi"
$SHA = git rev-parse --short HEAD

docker buildx build `
  --provenance=false --sbom=false --platform linux/amd64 `
  -t "${ECR}:latest" -t "${ECR}:${SHA}" --push .
```

**Step 2 — EC2 재배포 (SSM Send Command)**

```powershell
$params = @{ commands = @("/usr/local/bin/deploy-fastapi.sh latest") } | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText("$pwd\ssm-params.json", $params, [System.Text.UTF8Encoding]::new($false))

$result = aws ssm send-command `
  --instance-ids i-0c0f422cc734ff6ff `
  --document-name "AWS-RunShellScript" `
  --parameters file://ssm-params.json `
  --region ap-northeast-2 `
  --output json | ConvertFrom-Json

Start-Sleep -Seconds 25

aws ssm get-command-invocation `
  --command-id $result.Command.CommandId `
  --instance-id i-0c0f422cc734ff6ff `
  --region ap-northeast-2 --output json --no-cli-pager

Remove-Item ssm-params.json -Force
```

**Step 3 — 외부 검증**
```powershell
Invoke-RestMethod https://api.checkdang.xyz/heart-rate/test01?date=2026-05-14

$body = @{diets=@(@{foodName="test";calories=500})} | ConvertTo-Json
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
Invoke-WebRequest -Method Post -Uri https://api.checkdang.xyz/analyze/diet `
  -ContentType "application/json; charset=utf-8" -Body $bytes
```

### 5-4. 롤백 (특정 SHA로 되돌리기)

```powershell
# ECR 태그 목록 확인
aws ecr describe-images --repository-name chekdang-fastapi --region ap-northeast-2 `
  --query "sort_by(imageDetails,&imagePushedAt)[*].{Tags:imageTags,Pushed:imagePushedAt}" --output table

# 특정 SHA로 배포
$params = @{ commands = @("/usr/local/bin/deploy-fastapi.sh <git-sha>") } | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText("$pwd\ssm-params.json", $params, [System.Text.UTF8Encoding]::new($false))
aws ssm send-command --instance-ids i-0c0f422cc734ff6ff `
  --document-name "AWS-RunShellScript" `
  --parameters file://ssm-params.json `
  --region ap-northeast-2 --no-cli-pager
Remove-Item ssm-params.json -Force
```

### 5-5. EC2 직접 진단

```powershell
aws ssm start-session --target i-0c0f422cc734ff6ff --region ap-northeast-2
```

세션 안에서:
```bash
sudo -i
docker ps                                # 컨테이너 상태
docker logs checkdang-fastapi --tail 50  # 실시간 로그
docker inspect checkdang-fastapi         # 환경변수 확인
cat /usr/local/bin/deploy-fastapi.sh     # 배포 스크립트
```

### 5-6. 시크릿 회전

```powershell
$current = (aws secretsmanager get-secret-value --secret-id checkdang/springboot --region ap-northeast-2 --output json | ConvertFrom-Json).SecretString | ConvertFrom-Json
$current.GEMINI_API_KEY = "새 키"
$json = $current | ConvertTo-Json -Compress
[System.IO.File]::WriteAllText("$pwd\secret.json", $json, [System.Text.UTF8Encoding]::new($false))
aws secretsmanager put-secret-value --secret-id checkdang/springboot --secret-string file://secret.json --region ap-northeast-2 --no-cli-pager
Remove-Item secret.json -Force
```

그 후 Step 2 재배포로 새 키 반영.

---

## 6. 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| `/analyze/diet` 502 | Gemini 키 무효/쿼터 | Secrets Manager `GEMINI_API_KEY` 갱신 후 재배포 |
| 컨테이너 안 뜸 | 시크릿 fetch 실패 / IAM 권한 부족 | `aws iam list-role-policies --role-name checkdang-fastapi-ec2-role` 로 `SecretsManagerRead` 확인 |
| ECR pull 실패 | ECR 권한 부족 | EC2 Role에 `AmazonEC2ContainerRegistryReadOnly` 부여 |
| 새 코드 미반영 | `:latest` 안 받음 / 빌드 시 `--push` 누락 | `docker images` 로 다이제스트 확인, 강제 pull |
| Gemini 응답 MAX_TOKENS | thinking 토큰이 출력 한도 차지 | `max_output_tokens` 상향 또는 `thinking_config(thinking_budget=0)` |
| 회원가입 API 500 (`ResourceNotFoundException`) | `users` DynamoDB 테이블 누락 | `users` RDS 마이그레이션 (Phase 1 작업) |
| PowerShell `Invoke-RestMethod` 한글 깨짐 (`ì`) | PS 5.1 Latin-1 디코딩 버그 | `Invoke-WebRequest` + `$resp.RawContentStream` UTF-8 디코딩 |

---

## 7. 보안 정책

- EC2 SSH 키 미사용 — 모든 접근은 SSM Session Manager 경유 (CloudWatch에 감사 기록)
- 시크릿은 코드/환경변수 파일/Git에 평문 저장 금지
- Secrets Manager → IAM Role 기반 자격증명만 사용
- RDS PubliclyAccessible=false, 보안그룹에서 Fargate/베스천만 허용
- ALB는 HTTPS만 (ACM 와일드카드 인증서)
- 다음 Phase: WAF 적용, ALB Internal 전환, API Gateway + Cognito Authorizer 도입

---

## 8. 미해결 / 구조적 이슈

진행 중 발견되어 별도 작업으로 분리한 항목들:

| 이슈 | 근본 원인 | 영향 |
|---|---|---|
| `users` 가 DynamoDB에 저장됨 | 코드 구현이 설계(RDS)와 다름 | 회원가입 API 500 (테이블 자체 누락) |
| `payment_records` 가 DynamoDB | 동일 | RDS로 이전 예정 |
| `blood_glucose_record` 가 Spring Boot 처리 | 원래 FastAPI 담당 | 리팩터링 예정 |
| Spring Boot 자체 JWT 발급 | Cognito 미연동 | Cognito JWT로 전환 예정 |
| FastAPI 인증 없음 | 미구현 | 외부에서 임의 user_id로 호출 가능 — 출시 전 차단 필요 |
| `Diet.ai_result` / `Exercise.ai_result` 항상 NULL | 저장 로직 0건 (Git 전체 검색 확인) | AI 분석 호출마다 Gemini 재호출 + 히스토리 없음 |
