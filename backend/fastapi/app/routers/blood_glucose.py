from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional, List
from app.services.dynamodb import save_item, get_items_by_user

router = APIRouter(prefix="/blood-glucose", tags=["blood_glucose"])

TABLE_NAME = "blood_glucose_record"


class BloodGlucoseCreateRequest(BaseModel):
    timestamp: str        # ISO-8601 (예: "2025-05-04T09:30:00")
    level: int            # 혈당 수치 (mg/dL)
    meal_timing: str      # FASTING / BEFORE_MEAL / AFTER_MEAL / BEDTIME
    memo: Optional[str] = None


class BloodGlucoseResponse(BaseModel):
    user_date: str
    timestamp: str
    level: int
    meal_timing: str
    memo: Optional[str] = None


# 혈당 기록 저장
# PK: user_date = "{user_id}#{YYYY-MM-DD}", SK: timestamp
@router.post("/{user_id}", status_code=201)
async def save_blood_glucose(user_id: str, date: str, record: BloodGlucoseCreateRequest):
    if not record.timestamp:
        raise HTTPException(status_code=400, detail="timestamp는 필수입니다.")
    if record.level is None:
        raise HTTPException(status_code=400, detail="혈당 수치(level)는 필수입니다.")

    item = {
        "user_date": f"{user_id}#{date}",
        "timestamp": record.timestamp,
        "level": record.level,
        "meal_timing": record.meal_timing,
    }
    if record.memo:
        item["memo"] = record.memo

    save_item(TABLE_NAME, item)
    return {"message": "혈당 기록 저장 완료", "user_date": item["user_date"], "timestamp": record.timestamp}


# 특정 날짜의 혈당 기록 전체 조회
# query param: date=YYYY-MM-DD
@router.get("/{user_id}", response_model=List[BloodGlucoseResponse])
async def get_blood_glucose(user_id: str, date: str):
    items = get_items_by_user(TABLE_NAME, user_id, date)
    return items


# 특정 날짜 범위의 혈당 기록 조회
# query param: from_date=YYYY-MM-DD, to_date=YYYY-MM-DD
@router.get("/{user_id}/range")
async def get_blood_glucose_range(user_id: str, from_date: str, to_date: str):
    from datetime import date, timedelta

    try:
        start = date.fromisoformat(from_date)
        end = date.fromisoformat(to_date)
    except ValueError:
        raise HTTPException(status_code=400, detail="날짜 형식이 올바르지 않습니다. (예: 2025-05-04)")

    if start > end:
        raise HTTPException(status_code=400, detail="from_date는 to_date보다 이전이어야 합니다.")

    all_items = []
    current = start
    while current <= end:
        date_str = current.isoformat()
        items = get_items_by_user(TABLE_NAME, user_id, date_str)
        all_items.extend(items)
        current += timedelta(days=1)

    return {"data": all_items, "count": len(all_items)}
