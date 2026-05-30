import logging
import os
from datetime import datetime
from typing import Any, Optional

import httpx
from fastapi import APIRouter, Body, Depends, HTTPException
from pydantic import BaseModel, Field

from app.auth import verify_token
from app.services.dynamodb import get_items_by_user, save_item
from app.services.gemini import analyze_diet, analyze_health_report

logger = logging.getLogger(__name__)

# 예측 결과 저장 테이블 (PK=user_date, SK=predicted_at)
PREDICTION_TABLE = "blood_glucose_prediction"
MODEL_VERSION = os.getenv("MODEL_VERSION", "v1")


# 같은 EC2의 별도 컨테이너 (D-3/D-4 배포). 외부 노출 X — 메인 FastAPI만 호출.
# Linux EC2에서 컨테이너 안에서 host 접근: 172.17.0.1 (docker default bridge gateway).
# predictor 포트는 host의 127.0.0.1:8001 에 바인딩됨.
GLUCOSE_PREDICTOR_URL = os.getenv(
    "GLUCOSE_PREDICTOR_URL", "http://172.17.0.1:8001"
)


# prefix 제거: Spring Boot의 AiAnalysisClient가 /analyze/diet, /analyze/health-report 로 호출하기 때문
router = APIRouter(tags=["ai"])


class DietAnalyzeRequest(BaseModel):
    diets: list[dict[str, Any]]


class HealthReportAnalyzeRequest(BaseModel):
    """Spring Boot의 AiAnalysisClient.analyzeHealthReport가 보내는 페이로드.

    Pydantic은 예약어 `from`을 필드명으로 못 받으므로 alias로 처리한다.
    """

    user: dict[str, Any] = Field(default_factory=dict)
    from_: str | None = Field(default=None, alias="from")
    to: str | None = None
    diets: list[dict[str, Any]] = Field(default_factory=list)
    sleeps: list[dict[str, Any]] = Field(default_factory=list)
    exercises: list[dict[str, Any]] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class AnalyzeResponse(BaseModel):
    answer: str


def _wrap_gemini_call(callable_fn, *args, **kwargs) -> str:
    """Gemini 호출의 공통 예외 변환. RuntimeError는 500, 그 외는 502."""
    try:
        answer = callable_fn(*args, **kwargs)
    except RuntimeError as exc:
        # GEMINI_API_KEY 미설정 등
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Gemini API request failed.") from exc

    if not answer:
        raise HTTPException(status_code=502, detail="Gemini API response is empty.")

    return answer


@router.post("/analyze/diet", response_model=AnalyzeResponse)
def post_analyze_diet(request: DietAnalyzeRequest) -> AnalyzeResponse:
    """Spring Boot의 AiAnalysisClient가 호출하는 엔드포인트"""
    if not request.diets:
        return AnalyzeResponse(answer="분석할 식단 데이터가 아직 없어요.")

    answer = _wrap_gemini_call(analyze_diet, request.diets)
    return AnalyzeResponse(answer=answer)


@router.post("/analyze/health-report", response_model=AnalyzeResponse)
def post_analyze_health_report(request: HealthReportAnalyzeRequest) -> AnalyzeResponse:
    """Spring Boot의 AiAnalysisClient.analyzeHealthReport가 호출하는 엔드포인트.

    식단·수면·운동 데이터를 한 번에 받아 Markdown 종합 리포트를 반환한다.
    """
    payload = request.model_dump(by_alias=True)
    answer = _wrap_gemini_call(analyze_health_report, payload)
    return AnalyzeResponse(answer=answer)


# === 추후 구현 예정 ===


@router.post("/ai/predict/blood-glucose/{user_id}", dependencies=[Depends(verify_token)])
async def predict_blood_glucose(
    user_id: str,
    date: str,
    body: Optional[dict[str, Any]] = Body(default=None),
):
    """Seq2Seq GRU 혈당 예측 — 같은 EC2의 checkdang-glucose-predictor 컨테이너로 forward.

    body { glucose: [288 floats] } 있으면 그대로, 없으면 DynamoDB blood_glucose_record에서 조회.
    """
    url = f"{GLUCOSE_PREDICTOR_URL}/predict/blood-glucose/{user_id}"
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            resp = await client.post(url, params={"date": date}, json=body)
    except httpx.RequestError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"혈당 예측 서비스에 연결할 수 없습니다: {exc}",
        ) from exc

    if resp.status_code != 200:
        # predictor 측 422/500 등을 그대로 전달
        try:
            detail = resp.json().get("detail", resp.text)
        except Exception:
            detail = resp.text
        raise HTTPException(status_code=resp.status_code, detail=detail)

    result = resp.json()

    # 예측 결과 자동 저장 (실패해도 예측 응답은 유지)
    predicted_at = datetime.now().isoformat(timespec="seconds")
    saved = False
    try:
        item = {
            "user_date": f"{user_id}#{date}",
            "predicted_at": predicted_at,
            "predictions": [str(v) for v in result.get("predictions", [])],
            "horizon_minutes": result.get("horizon_minutes"),
            "interval_minutes": 5,
            "source": result.get("source"),
            "model_version": MODEL_VERSION,
        }
        save_item(PREDICTION_TABLE, item)
        saved = True
    except Exception as exc:
        logger.warning("예측 결과 저장 실패 (user=%s date=%s): %s", user_id, date, exc)

    result["predicted_at"] = predicted_at
    result["saved"] = saved
    return result


def _normalize_item(item: dict) -> dict:
    """DynamoDB Decimal 등을 JSON 직렬화 가능한 타입으로 변환.

    - 숫자 메타(horizon_minutes/interval_minutes)는 int
    - predictions는 문자열로 저장돼 있으므로 float 리스트로 복원
    """
    out = dict(item)
    for k in ("horizon_minutes", "interval_minutes"):
        if k in out and out[k] is not None:
            out[k] = int(out[k])
    if "predictions" in out:
        out["predictions"] = [float(v) for v in out["predictions"]]
    return out


@router.get("/ai/predictions/{user_id}", dependencies=[Depends(verify_token)])
async def get_predictions(user_id: str, date: str):
    """해당 날짜의 예측 이력 전체 조회 (predicted_at 내림차순)."""
    items = get_items_by_user(PREDICTION_TABLE, user_id, date)
    items.sort(key=lambda x: x.get("predicted_at", ""), reverse=True)
    return [_normalize_item(i) for i in items]


@router.get("/ai/predictions/{user_id}/latest", dependencies=[Depends(verify_token)])
async def get_latest_prediction(user_id: str, date: str):
    """해당 날짜의 최신 예측 1건 조회."""
    items = get_items_by_user(PREDICTION_TABLE, user_id, date)
    if not items:
        raise HTTPException(status_code=404, detail="해당 날짜의 예측 기록이 없습니다.")
    items.sort(key=lambda x: x.get("predicted_at", ""), reverse=True)
    return _normalize_item(items[0])


@router.post("/ai/analyze/pain/{user_id}", dependencies=[Depends(verify_token)])
async def analyze_pain(user_id: str):
    pass
