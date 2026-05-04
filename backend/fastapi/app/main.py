from fastapi import FastAPI
from app.routers import blood_glucose, heart_rate, step_calorie, ai

app = FastAPI()

app.include_router(blood_glucose.router)
app.include_router(heart_rate.router)
app.include_router(step_calorie.router)
app.include_router(ai.router)
