from fastapi import APIRouter
from app.models.schemas import HeartRateRecord
from app.services.dynamodb import save_item, get_items_by_user

router = APIRouter(prefix="/heart-rate", tags=["heart_rate"])

TABLE_NAME = "heart_rate"


# 심박수 저장
# data-flow.md 참고: heart_rate 테이블 - user_date, timestamp, bpm, device_id, ibi
@router.post("/{user_id}")
async def save_heart_rate(user_id: str, date: str, record: HeartRateRecord):
    item = {
        "user_date": f"{user_id}#{date}",   # PK: user_id + date
        "timestamp": record.timestamp,       # SK
        "bpm": record.bpm,
        "device_id": record.device_id,
        "ibi": str(record.ibi) if record.ibi else None
    }
    save_item(TABLE_NAME, item)
    return {"message": "심박수 저장 완료"}


# 심박수 조회
# data-flow.md 참고: 혈당 예측 AI 입력 데이터로 활용
@router.get("/{user_id}")
async def get_heart_rate(user_id: str, date: str):
    items = get_items_by_user(TABLE_NAME, user_id, date)
    return {"data": items}
