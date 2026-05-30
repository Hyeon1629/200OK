from fastapi import Depends, FastAPI

from app.auth import verify_token
from app.routers import ai, blood_glucose, heart_rate, step_calorie

app = FastAPI(title="checkdang FastAPI")

# Cognito JWT 검증
# - 프론트 직통 라우터(blood_glucose/heart_rate/step_calorie)는 라우터 전체 보호
# - ai 라우터는 /analyze/* (Spring 내부 위임, 무인증) 와 /ai/* (프론트 직통, 보호)가 섞여 있어
#   라우터 레벨 인증을 걸지 않고 ai.py 내부에서 /ai/* 엔드포인트에만 개별 적용
auth = [Depends(verify_token)]
app.include_router(blood_glucose.router, dependencies=auth)
app.include_router(heart_rate.router, dependencies=auth)
app.include_router(step_calorie.router, dependencies=auth)
app.include_router(ai.router)


@app.get("/health")
def health() -> dict[str, str]:
    """EC2/ALB 헬스체크용"""
    return {"status": "ok"}
