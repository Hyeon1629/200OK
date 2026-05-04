from pydantic import BaseModel
from typing import Optional


class BloodGlucoseRecord(BaseModel):
    user_date: str
    timestamp: str
    level: int
    meal_timing: str
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
