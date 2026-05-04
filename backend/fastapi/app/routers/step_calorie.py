from fastapi import APIRouter
from app.models.schemas import StepCalorieRecord
from app.services.dynamodb import save_item, get_items_by_user

router = APIRouter(prefix="/step-calorie", tags=["step_calorie"])

TABLE_NAME = "step_calorie"


# 걸음수/소비칼로리 저장
# data-flow.md 참고: step_calorie 테이블 - user_date, timestamp, step_count, calorie, device_id
@router.post("/{user_id}")
async def save_step_calorie(user_id: str, date: str, record: StepCalorieRecord):
    item = {
        "user_date": f"{user_id}#{date}",   # PK: user_id + date
        "timestamp": record.timestamp,       # SK
        "step_count": record.step_count,
        "calorie": str(record.calorie),
        "device_id": record.device_id
    }
    save_item(TABLE_NAME, item)
    return {"message": "걸음수/소비칼로리 저장 완료"}


# 걸음수/소비칼로리 조회
# data-flow.md 참고: 혈당 예측 AI 입력 데이터로 활용
@router.get("/{user_id}")
async def get_step_calorie(user_id: str, date: str):
    items = get_items_by_user(TABLE_NAME, user_id, date)
    return {"data": items}
