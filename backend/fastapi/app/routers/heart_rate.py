from fastapi import APIRouter
from app.models.schemas import HeartRateRecord
from app.services.dynamodb import save_item, get_items_by_user

router = APIRouter(prefix="/heart-rate", tags=["heart_rate"])

TABLE_NAME = "heart_rate"


# 심박수 저장
# PK: user_date = "{user_id}#{YYYY-MM-DD}", SK: source_id (멱등 키)
# 같은 source_id 재전송 시 putItem upsert로 1건만 유지
@router.post("/{user_id}")
async def save_heart_rate(user_id: str, date: str, record: HeartRateRecord):
    item = {
        "user_date": f"{user_id}#{date}",
        "source_id": record.source_id,
        "timestamp": record.timestamp,
        "bpm": record.bpm,
        "device_id": record.device_id,
        "ibi": str(record.ibi) if record.ibi else None
    }
    save_item(TABLE_NAME, item)
    return {"message": "심박수 저장 완료", "user_date": item["user_date"], "source_id": record.source_id}


# 심박수 조회
# data-flow.md 참고: 혈당 예측 AI 입력 데이터로 활용
@router.get("/{user_id}")
async def get_heart_rate(user_id: str, date: str):
    items = get_items_by_user(TABLE_NAME, user_id, date)
    return {"data": items}
