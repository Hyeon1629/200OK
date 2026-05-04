from fastapi import APIRouter

router = APIRouter(prefix="/ai", tags=["ai"])


@router.post("/predict/blood-glucose/{user_id}")
async def predict_blood_glucose(user_id: str):
    pass


@router.post("/analyze/pain/{user_id}")
async def analyze_pain(user_id: str):
    pass
