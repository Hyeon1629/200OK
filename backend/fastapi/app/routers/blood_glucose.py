from fastapi import APIRouter

router = APIRouter(prefix="/blood-glucose", tags=["blood_glucose"])


@router.get("/{user_id}")
async def get_blood_glucose(user_id: str):
    pass


@router.post("/{user_id}")
async def save_blood_glucose(user_id: str):
    pass
