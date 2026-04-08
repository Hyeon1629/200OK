<<<<<<< HEAD
# Checkdang - 당뇨 환자 관리 앱 백엔드

당뇨 환자와 의료진을 연결하는 관리 플랫폼의 Spring Boot 백엔드 서버입니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Database | MySQL 8 |
| ORM | Spring Data JPA (Hibernate) |
| Security | Spring Security |
| Build Tool | Gradle |
| Etc | Lombok, Spring Actuator |

---

## 프로젝트 구조

```
src/main/java/com/checkdang/
├── CheckdangApplication.java       # 애플리케이션 진입점
├── config/
│   └── SecurityConfig.java         # Spring Security 설정
├── controller/
│   └── UserController.java         # 인증 API 컨트롤러
├── domain/
│   └── User.java                   # 사용자 엔티티 (PATIENT / DOCTOR / ADMIN)
├── dto/
│   ├── LoginRequest.java           # 로그인 요청 DTO
│   ├── SignupRequest.java          # 회원가입 요청 DTO
│   ├── UserDto.java
│   └── UserResponse.java           # 응답 DTO
├── repository/
│   └── UserRepository.java         # 사용자 JPA 레포지토리
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
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "PATIENT",
  "createdAt": "2026-04-08T12:00:00"
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
  "id": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "PATIENT",
  "createdAt": "2026-04-08T12:00:00"
}
```

---

## 로컬 실행 방법

### 1. 사전 요구사항

- Java 17 이상
- MySQL 8 실행 중

### 2. 데이터베이스 생성

```sql
CREATE DATABASE checkdang;
```

### 3. 환경변수 설정

`src/main/resources/application.yaml`을 직접 수정하거나, 아래 환경변수를 설정합니다. ([환경변수 설정 방법](#환경변수-설정-방법) 참고)

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

`src/main/resources/application.yaml`에서 다음 항목을 환경에 맞게 수정합니다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://<DB_HOST>:<DB_PORT>/<DB_NAME>
    username: <DB_USERNAME>
    password: <DB_PASSWORD>
```

| 항목 | 설명 | 예시 |
|------|------|------|
| `DB_HOST` | MySQL 호스트 주소 | `localhost` |
| `DB_PORT` | MySQL 포트 | `3306` |
| `DB_NAME` | 데이터베이스 이름 | `checkdang` |
| `DB_USERNAME` | DB 접속 계정 | `root` |
| `DB_PASSWORD` | DB 접속 비밀번호 | `yourpassword` |

> **주의:** `application.yaml`에 실제 DB 계정 정보를 직접 입력하지 말고, `.gitignore`에 등록하거나 환경변수로 분리하여 관리하세요.

