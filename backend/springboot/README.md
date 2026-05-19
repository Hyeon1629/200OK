# Checkdang - 당뇨 환자 관리 앱 백엔드

당뇨 환자와 의료진을 연결하는 관리 플랫폼의 Spring Boot 백엔드 서버입니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Database | Amazon DynamoDB |
| Security | Spring Security |
| Build Tool | Gradle |
| Etc | Lombok, Spring Actuator |

---

## 프로젝트 구조

```
src/main/java/com/checkdang/
├── CheckdangApplication.java       # 애플리케이션 진입점
├── config/
│   ├── DynamoDbConfig.java         # DynamoDB 클라이언트 및 테이블 설정
│   ├── SecurityConfig.java         # Spring Security 설정
│   └── AppConfig.java
├── controller/
│   └── UserController.java         # 인증 API 컨트롤러
├── domain/
│   └── User.java                   # 사용자 엔티티 (PATIENT / DOCTOR / ADMIN)
├── dto/
│   ├── LoginRequest.java           # 로그인 요청 DTO
│   ├── SignupRequest.java          # 회원가입 요청 DTO
│   ├── TokenResponse.java          # JWT 토큰 응답 DTO
│   ├── UserDto.java
│   └── UserResponse.java           # 응답 DTO
├── repository/
│   └── UserRepository.java         # DynamoDB 기반 사용자 레포지토리
├── security/
│   ├── jwt/
│   │   ├── JwtAuthenticationFilter.java
│   │   └── JwtTokenProvider.java
│   └── oauth2/
│       ├── CustomOAuth2UserService.java
│       └── OAuth2SuccessHandler.java
└── service/
    └── UserService.java            # 인증 비즈니스 로직
```

---

## API 엔드포인트

Base URL: `http://localhost:8080`

### 회원가입

```
POST /api/auth/signup
```

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동",
  "role": "PATIENT"
}
```

- `role` 값: `PATIENT` | `DOCTOR` | `ADMIN`

**Response** `201 Created`
```json
{
  "id": "uuid",
  "email": "user@example.com",
  "name": "홍길동",
  "role": "PATIENT",
  "createdAt": "2026-04-08T12:00:00Z"
}
```

---

### 로그인

```
POST /api/auth/login
```

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response** `200 OK`
```json
{
  "token": "eyJhbGci...",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "홍길동",
    "role": "PATIENT",
    "createdAt": "2026-04-08T12:00:00Z"
  }
}
```

---

## 로컬 실행 방법

### 1. 사전 요구사항

- Java 17 이상
- AWS 자격증명 (또는 DynamoDB Local)

### 2. DynamoDB 테이블 생성

AWS CLI로 테이블 생성:

```bash
aws dynamodb create-table \
  --table-name users \
  --attribute-definitions AttributeName=email,AttributeType=S \
  --key-schema AttributeName=email,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region ap-northeast-2
```

또는 DynamoDB Local 사용 시:

```bash
docker run -p 8000:8000 amazon/dynamodb-local
```

### 3. 환경변수 설정

`.env` 파일 또는 환경변수로 설정합니다. (`.env.example` 참고)

### 4. 빌드 및 실행

```bash
# 테스트 제외하고 빌드
./gradlew build -x test

# 실행
./gradlew bootRun
```

서버가 `http://localhost:8080`에서 실행됩니다.

### 5. 헬스체크

```bash
curl http://localhost:8080/actuator/health
```

---

## 환경변수 설정 방법

| 항목 | 설명 | 예시 |
|------|------|------|
| `AWS_REGION` | AWS 리전 | `ap-northeast-2` |
| `AWS_ACCESS_KEY_ID` | AWS 액세스 키 | `AKIA...` |
| `AWS_SECRET_ACCESS_KEY` | AWS 시크릿 키 | `...` |
| `DYNAMODB_ENDPOINT` | DynamoDB Local 엔드포인트 (로컬 개발 시) | `http://localhost:8000` |
| `JWT_SECRET` | JWT 서명 키 | `your-secret-key` |
| `JWT_EXPIRATION` | JWT 만료 시간 (ms) | `86400000` |
| `GOOGLE_CLIENT_ID` | Google OAuth2 클라이언트 ID | `...` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 클라이언트 시크릿 | `...` |
| `KAKAO_CLIENT_ID` | Kakao OAuth2 클라이언트 ID | `...` |
| `KAKAO_CLIENT_SECRET` | Kakao OAuth2 클라이언트 시크릿 | `...` |
| `OAUTH2_REDIRECT_URI` | OAuth2 리다이렉트 URI | `http://localhost:8080/oauth2/callback` |

> **주의:** `.env` 파일에 실제 자격증명을 직접 입력하지 말고, `.gitignore`에 등록하여 관리하세요.
