# Seq2Seq GRU 혈당 예측 추론 컨테이너
# - 학습 산출물(artifacts/)을 이미지에 동봉
# - 추론만 — torch CPU wheel로 이미지 크기 최소화

FROM python:3.11-slim

WORKDIR /app

# 시스템 패키지: curl (헬스체크용)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# torch CPU wheel + 나머지 의존성
# (cache-dir 비활성으로 이미지 크기 절감)
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 앱 코드 + 학습 산출물
COPY app/ ./app/
COPY artifacts/ ./artifacts/

# AWS 자격 — EC2 IAM Role 또는 ENV로 주입 (Dockerfile에 키 박지 않음)
ENV AWS_REGION=ap-northeast-2
ENV ARTIFACTS_DIR=/app/artifacts
ENV DYNAMODB_TABLE=blood_glucose_record

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -f http://127.0.0.1:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
