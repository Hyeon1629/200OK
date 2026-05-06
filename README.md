# Backend Update Guide

## PR에 포함할 백엔드 변경 파일

이번 Gemini 식단 조언 기능 기준으로 PR에 포함할 백엔드 파일은 아래와 같습니다.

```text
A  backend/fastapi/.env.example
A  backend/fastapi/.gitignore
A  backend/fastapi/main.py
A  backend/fastapi/requirements.txt
M  backend/springboot/.env.example
M  backend/springboot/gradlew.bat
M  backend/springboot/src/main/java/com/checkdang/config/SecurityConfig.java
A  backend/springboot/src/main/java/com/checkdang/controller/AiAdviceController.java
A  backend/springboot/src/main/java/com/checkdang/controller/GeminiReportController.java
A  backend/springboot/src/main/java/com/checkdang/dto/AiAdviceResponse.java
A  backend/springboot/src/main/java/com/checkdang/service/AiAnalysisClient.java
A  backend/springboot/src/main/java/com/checkdang/service/GeminiService.java
M  backend/springboot/src/main/resources/application.yaml
```

## PR에 포함하지 않을 파일

아래 파일들은 이번 Gemini 식단 조언 PR에 넣지 않는 것을 권장합니다.

```text
A  backend/data-flow.md
M  backend/springboot/src/main/java/com/checkdang/domain/Diet.java
M  backend/springboot/src/main/java/com/checkdang/domain/Sleep.java
M  backend/springboot/src/main/java/com/checkdang/dto/SleepResponse.java
M  backend/springboot/src/main/java/com/checkdang/dto/SleepSyncRequest.java
M  backend/springboot/src/main/java/com/checkdang/repository/SleepRepository.java
M  backend/springboot/src/main/java/com/checkdang/service/SleepService.java
```

`Diet.java`는 현재 기준 폴더와 비교했을 때 기존 `aiResult`, `aiRecommendation` 필드가 제거된 변경으로 보입니다. 이 제거가 의도한 작업이 아니라면 PR에 포함하지 않는 것이 안전합니다.

Sleep 관련 파일들은 Gemini 식단 조언 기능과 직접 관련이 없으므로, 별도 작업이 아니라면 제외합니다.

절대 올리면 안 되는 로컬/비밀 파일:

```text
backend/springboot/.env
backend/fastapi/.env
backend/springboot/bootRun.local.log
backend/springboot/build/
backend/springboot/.gradle/
backend/fastapi/__pycache__/
```

## 백엔드 구조

앱에서 Gemini 식단 조언을 확인할 때 요청 흐름은 다음과 같습니다.

```text
Android 앱
-> Spring Boot /api/ai/demo-diet-advice
-> AiAnalysisClient
-> FastAPI /analyze/diet
-> Gemini API
-> FastAPI 응답
-> Spring Boot 응답
-> Android 화면 표시
```

## FastAPI 변경 사항

### `backend/fastapi/main.py`

Gemini API를 호출하는 Python FastAPI 서버입니다.

- `/health` 엔드포인트를 제공합니다.
- `/analyze/diet` 엔드포인트를 제공합니다.
- Spring Boot에서 받은 식단 데이터를 Gemini 프롬프트에 넣습니다.
- Gemini 응답의 텍스트를 `answer` 필드로 반환합니다.
- Gemini API 호출 실패 시 `502` 에러를 반환합니다.

### `backend/fastapi/requirements.txt`

FastAPI 서버 실행에 필요한 Python 패키지 목록입니다.

```text
fastapi
uvicorn
python-dotenv
google-genai
```

### `backend/fastapi/.gitignore`

FastAPI 로컬 실행 파일을 GitHub에 올리지 않도록 제외합니다.

```text
.env
__pycache__/
.venv/
```

### `backend/fastapi/.env.example`

다른 사람이 로컬에서 FastAPI를 실행할 때 필요한 환경변수 예시 파일입니다.

```env
GEMINI_API_KEY=your-gemini-api-key
```

실제 키가 들어가는 `.env`는 GitHub에 올리지 않습니다.

## Spring Boot 변경 사항

### `backend/springboot/src/main/java/com/checkdang/controller/AiAdviceController.java`

Android 앱에서 호출할 AI 식단 조언 API 컨트롤러입니다.

- `GET /api/ai/diet-advice`
  - 로그인된 사용자의 실제 식단 데이터를 조회합니다.
  - 조회한 식단 데이터를 FastAPI로 보내 Gemini 분석 결과를 받습니다.
- `GET /api/ai/demo-diet-advice`
  - 로그인 없이 테스트 가능한 데모 API입니다.
  - 샘플 식단 데이터로 Gemini 조언을 요청합니다.
  - Android 앱에서 현재 이 API를 호출합니다.

### `backend/springboot/src/main/java/com/checkdang/service/AiAnalysisClient.java`

Spring Boot에서 FastAPI 서버로 요청을 보내는 클라이언트입니다.

- `ai.server-url` 설정값을 사용합니다.
- 기본 로컬 주소는 `http://localhost:8000`입니다.
- FastAPI의 `/analyze/diet` 엔드포인트로 식단 데이터를 전송합니다.
- FastAPI 응답에서 `answer` 값을 꺼내 반환합니다.

### `backend/springboot/src/main/java/com/checkdang/dto/AiAdviceResponse.java`

Android 앱으로 내려줄 AI 응답 DTO입니다.

```json
{
  "answer": "Gemini가 생성한 식단 조언"
}
```

### `backend/springboot/src/main/java/com/checkdang/config/SecurityConfig.java`

API 요청과 OAuth 요청의 보안 설정을 분리합니다.

- `/api/**` 요청은 JWT 기반 API 보안 체인을 사용합니다.
- `/api/auth/**`, `/api/ai/demo-diet-advice`, `/actuator/health`는 인증 없이 허용합니다.
- 인증이 필요한 API에서 로그인 페이지로 리다이렉트하지 않고 `401`을 반환합니다.
- OAuth 관련 요청은 별도 보안 체인에서 처리합니다.

이 설정이 없으면 Android 앱에서 API 호출 시 OAuth 로그인 화면으로 리다이렉트될 수 있습니다.

### `backend/springboot/src/main/resources/application.yaml`

AI 서버와 Gemini 설정을 추가하고 로컬 DB 실행이 가능하도록 설정합니다.

```yaml
jpa:
  hibernate:
    ddl-auto: update

ai:
  server-url: ${AI_SERVER_URL:http://localhost:8000}

gemini:
  api-key: ${GEMINI_API_KEY:}
  model: ${GEMINI_MODEL:gemini-2.5-flash}
```

`ddl-auto: update`는 로컬 개발에서 테이블이 없을 때 자동 생성되도록 하기 위한 설정입니다.

### `backend/springboot/.env.example`

Spring Boot 로컬 실행에 필요한 환경변수 예시입니다.

주요 추가 항목:

```env
RDS_HOST=localhost
RDS_DB_NAME=checkdang
RDS_USERNAME=root
RDS_PASSWORD=your-db-password
AI_SERVER_URL=http://localhost:8000
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=gemini-2.5-flash
```

실제 값이 들어가는 `.env`는 GitHub에 올리지 않습니다.

### `backend/springboot/gradlew.bat`

Windows에서 Spring Boot 실행 시 발생하던 Gradle wrapper 실행 오류를 수정합니다.

기존 문제:

```text
Error: -classpath requires class path specification
```

수정 내용:

```bat
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" ... -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
```

이 수정으로 Windows에서 `.\gradlew.bat bootRun` 실행이 가능해집니다.

### `backend/springboot/src/main/java/com/checkdang/controller/GeminiReportController.java`

식단, 수면, 운동 데이터를 모아 Gemini로 건강 리포트를 생성하는 API입니다.

- `GET /api/ai/reports/health`
- 로그인된 사용자의 최근 데이터를 조회합니다.
- Gemini에 건강 리포트 프롬프트를 보냅니다.
- 한국어 Markdown 형식의 리포트를 반환합니다.

현재 Android 앱의 식단 조언 버튼은 이 API가 아니라 `/api/ai/demo-diet-advice`를 호출합니다.

### `backend/springboot/src/main/java/com/checkdang/service/GeminiService.java`

Spring Boot에서 Gemini API를 직접 호출하기 위한 서비스입니다.

- `gemini.api-key`를 사용합니다.
- `gemini.model`을 사용합니다.
- 프롬프트를 Gemini API에 보내고 첫 번째 응답 텍스트를 반환합니다.

## 로컬 실행 방법

Gemini 응답까지 확인하려면 FastAPI와 Spring Boot가 둘 다 실행되어야 합니다.

## 1. FastAPI 실행

```powershell
cd C:\Users\user\Desktop\200OK-main\200OK-main\backend\fastapi
copy .env.example .env
```

`.env`를 열어서 실제 Gemini API 키를 넣습니다.

```env
GEMINI_API_KEY=actual-gemini-api-key
```

그 다음 실행합니다.

```powershell
pip install -r requirements.txt
python -m uvicorn main:app --reload --port 8000
```

확인:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

정상이라면 아래처럼 나옵니다.

```json
{
  "status": "ok"
}
```

## 2. Spring Boot 실행

```powershell
cd C:\Users\user\Desktop\200OK-main\200OK-main\backend\springboot
copy .env.example .env
```

`.env`를 열어서 본인 로컬 값으로 수정합니다.

```env
RDS_HOST=localhost
RDS_DB_NAME=checkdang
RDS_USERNAME=root
RDS_PASSWORD=your-db-password
JWT_SECRET=your-base64-jwt-secret
AI_SERVER_URL=http://localhost:8000
```

MySQL에 DB가 없으면 먼저 생성합니다.

```sql
CREATE DATABASE checkdang;
```

Spring Boot 실행:

```powershell
.\gradlew.bat bootRun
```

확인:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

정상이라면 아래처럼 나옵니다.

```json
{
  "status": "UP"
}
```

## 3. Gemini 식단 조언 API 확인

FastAPI와 Spring Boot가 모두 실행된 상태에서 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8080/api/ai/demo-diet-advice
```

정상 응답 예시:

```json
{
  "answer": "김밥과 라면은 탄수화물과 나트륨이 높은 편입니다..."
}
```

이 응답이 나오면 Android 앱에서도 `Gemini 식단 조언 받기` 버튼으로 답변을 확인할 수 있습니다.

## GitHub 웹에서 업로드할 때 경로

GitHub에서 이미 `backend` 폴더 안에 들어간 상태라면, 새 파일을 만들 때 `backend/`를 또 붙이면 안 됩니다.

예시:

```text
fastapi/main.py
fastapi/requirements.txt
fastapi/.gitignore
fastapi/.env.example
springboot/src/main/java/com/checkdang/controller/AiAdviceController.java
```

이미 `backend` 폴더 안에서 작업 중이면 위처럼 작성해야 합니다.

잘못된 예:

```text
backend/fastapi/main.py
```

이렇게 쓰면 `backend/backend/fastapi/main.py`가 생길 수 있습니다.

