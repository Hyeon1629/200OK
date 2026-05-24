from fastapi import FastAPI

from app.routers import ai, blood_glucose, heart_rate, step_calorie

app = FastAPI(title="checkdang FastAPI")

app.include_router(blood_glucose.router)
app.include_router(heart_rate.router)
app.include_router(step_calorie.router)
app.include_router(ai.router)


@app.get("/health")
def health() -> dict[str, str]:
    """EC2/ALB 헬스체크용"""
    return {"status": "ok"}
