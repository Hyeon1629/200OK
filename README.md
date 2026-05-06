# 앱 실행 방법


1. 사전 준비

env.example에 있는 RDS 비번 채우기 #backend, MySQL 비번 

fastapi에 .env 파일 하나 만들고 gemini_api_key 넣기

입력 형식: GEMINI_API_KEY = 

requirements.txt 다운로드

pip install -r requirements.txt

2. FastAPI 실행

에뮬레이터 새로 만들어서 app 있는 폴더에서 열기(Pixel 7)

cd C:\Users\user\Desktop\200OK-main\200OK-main\backend_temp\fastapi 

python -m uvicorn main:app --reload --port 8000

3. Spring Boot 실행

cd C:\Users\user\Desktop\200OK-main\200OK-main\backend_temp\springboot

.\gradlew.bat bootRun

4. 안드로이드 스튜디오에서 앱 실행

식사 상세 화면 이동
-> Gemini 식단 조언 받기 버튼 클릭
-> 버튼 아래에 Gemini 답변 표시 확인

AI 작동 안 되면 확인 사항

FastAPI 서버가 꺼져 있음
Spring Boot 서버가 꺼져 있음
backend/fastapi/.env의 GEMINI_API_KEY가 잘못됨
Gemini API 키가 비활성화됨
FastAPI 실행 후 .env를 수정했지만 서버를 재시작하지 않음

