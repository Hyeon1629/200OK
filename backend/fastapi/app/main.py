from fastapi import Depends, FastAPI

from app.auth import verify_token
from app.routers import ai, blood_glucose, heart_rate, step_calorie

app = FastAPI(title="checkdang FastAPI")

# Cognito JWT 검증 — /health 제외 전 라우터 보호
auth = [Depends(verify_token)]
app.include_router(blood_glucose.router, dependencies=auth)
app.include_router(heart_rate.router, dependencies=auth)
app.include_router(step_calorie.router, dependencies=auth)
app.include_router(ai.router, dependencies=auth)


@app.get("/health")
def health() -> dict[str, str]:
    """EC2/ALB 헬스체크용"""
    return {"status": "ok"}
