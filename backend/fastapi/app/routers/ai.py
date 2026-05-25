import os
from typing import Any, Optional

import httpx
from fastapi import APIRouter, Body, HTTPException
from pydantic import BaseModel, Field

from app.services.gemini import analyze_diet, analyze_health_report


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


@router.post("/ai/predict/blood-glucose/{user_id}")
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

    return resp.json()


@router.post("/ai/analyze/pain/{user_id}")
async def analyze_pain(user_id: str):
    pass
