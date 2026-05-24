from pydantic import BaseModel
from typing import Optional


class BloodGlucoseRecord(BaseModel):
    # 참고: 실제 라우터는 BloodGlucoseCreateRequest 사용 — 이 클래스는 미사용
    user_date: str   # PK: "{user_id}#{YYYY-MM-DD}"
    source_id: str   # SK
    timestamp: str   # ISO-8601
    level: int       # 혈당 수치 (mg/dL)
    meal_timing: str # FASTING / BEFORE_MEAL / AFTER_MEAL / BEDTIME
    memo: Optional[str] = None


class HeartRateRecord(BaseModel):
    source_id: str   # SK 겸 멱등 키
    timestamp: str
    bpm: int
    device_id: str
    ibi: Optional[float] = None


class StepCalorieRecord(BaseModel):
    source_id: str   # SK 겸 멱등 키
    timestamp: str
    step_count: int
    calorie: float
    device_id: str
