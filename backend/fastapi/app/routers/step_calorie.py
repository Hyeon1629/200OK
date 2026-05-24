from fastapi import APIRouter
from app.models.schemas import StepCalorieRecord
from app.services.dynamodb import save_item, get_items_by_user

router = APIRouter(prefix="/step-calorie", tags=["step_calorie"])

TABLE_NAME = "step_calorie"


# 걸음수/소비칼로리 저장
# PK: user_date = "{user_id}#{YYYY-MM-DD}", SK: source_id (멱등 키)
# 같은 source_id 재전송 시 putItem upsert로 1건만 유지
@router.post("/{user_id}")
async def save_step_calorie(user_id: str, date: str, record: StepCalorieRecord):
    item = {
        "user_date": f"{user_id}#{date}",
        "source_id": record.source_id,
        "timestamp": record.timestamp,
        "step_count": record.step_count,
        "calorie": str(record.calorie),
        "device_id": record.device_id
    }
    save_item(TABLE_NAME, item)
    return {"message": "걸음수/소비칼로리 저장 완료", "user_date": item["user_date"], "source_id": record.source_id}


# 걸음수/소비칼로리 조회
# data-flow.md 참고: 혈당 예측 AI 입력 데이터로 활용
@router.get("/{user_id}")
async def get_step_calorie(user_id: str, date: str):
    items = get_items_by_user(TABLE_NAME, user_id, date)
    return {"data": items}
