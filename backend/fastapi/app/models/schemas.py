from pydantic import BaseModel
from typing import Optional


class BloodGlucoseRecord(BaseModel):
    user_date: str   # PK: "{user_id}#{YYYY-MM-DD}"
    timestamp: str   # SK: ISO-8601
    level: int       # 혈당 수치 (mg/dL)
    meal_timing: str # FASTING / BEFORE_MEAL / AFTER_MEAL / BEDTIME
    memo: Optional[str] = None


class HeartRateRecord(BaseModel):
    user_date: str
    timestamp: str
    bpm: int
    device_id: str
    ibi: Optional[float] = None


class StepCalorieRecord(BaseModel):
    user_date: str
    timestamp: str
    step_count: int
    calorie: float
    device_id: str
