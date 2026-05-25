"""혈당 예측 추론 FastAPI 서버.

학습 산출물(artifacts/best_seq2seq_gru.pt + scaler.pkl + metadata.json)을
컨테이너 시작 시 메모리에 로드해 추론에 사용한다.

엔드포인트:
- GET  /health                                       헬스체크
- POST /predict/blood-glucose/{user_id}?date=...     혈당 예측
    - body { "glucose": [288 floats] } 있으면 그대로 사용
    - 없으면 DynamoDB blood_glucose_record 에서 24h 조회 (288 step 부족 시 422)
    - 반환: { "predictions": [36 floats], "horizon_minutes": 180, "source": "body"|"db" }
"""

from __future__ import annotations

import json
import logging
import os
import pickle
from pathlib import Path
from typing import Optional

import boto3
import numpy as np
import torch
from boto3.dynamodb.conditions import Key
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.model import Seq2SeqGRU


logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


ARTIFACTS_DIR = Path(os.getenv("ARTIFACTS_DIR", "/app/artifacts"))
AWS_REGION = os.getenv("AWS_REGION", "ap-northeast-2")
DYNAMODB_TABLE = os.getenv("DYNAMODB_TABLE", "blood_glucose_record")


# 컨테이너 startup 시 채워짐. 추론에서 readonly로 사용.
_state: dict = {
    "model": None,
    "scaler": None,
    "metadata": None,
    "dynamodb_table": None,
}


app = FastAPI(title="checkdang glucose predictor")


@app.on_event("startup")
def load_artifacts() -> None:
    """metadata 로 모델 재구성 → state_dict load → scaler load."""
    metadata_path = ARTIFACTS_DIR / "metadata.json"
    pt_path = ARTIFACTS_DIR / "best_seq2seq_gru.pt"
    scaler_path = ARTIFACTS_DIR / "scaler.pkl"

    if not metadata_path.exists() or not pt_path.exists() or not scaler_path.exists():
        raise RuntimeError(f"artifacts 누락 — {ARTIFACTS_DIR} 확인 필요")

    with metadata_path.open() as f:
        metadata = json.load(f)
    _state["metadata"] = metadata

    model = Seq2SeqGRU(
        input_dim=1,
        hidden_dim=metadata["hidden_dim"],
        num_layers=metadata["num_layers"],
        output_len=metadata["output_len"],
        dropout=metadata["dropout"],
    )
    model.load_state_dict(torch.load(pt_path, map_location="cpu"))
    model.eval()
    _state["model"] = model

    with scaler_path.open("rb") as f:
        _state["scaler"] = pickle.load(f)

    _state["dynamodb_table"] = boto3.resource("dynamodb", region_name=AWS_REGION).Table(DYNAMODB_TABLE)

    logger.info(
        "artifacts loaded: input_len=%d output_len=%d horizon=%dmin (train MAE=%.2f)",
        metadata["input_len"], metadata["output_len"],
        metadata["horizon_minutes"], metadata["test_mae_mg_dl"],
    )


@app.get("/health")
def health() -> dict:
    ready = all(_state[k] is not None for k in ("model", "scaler", "metadata"))
    return {"status": "ok" if ready else "loading", "model_loaded": ready}


class PredictRequest(BaseModel):
    glucose: list[float]


class PredictResponse(BaseModel):
    predictions: list[float]
    horizon_minutes: int
    source: str  # "body" | "db"


def _fetch_glucose_from_db(user_id: str, date: str, required_len: int) -> list[float]:
    """DynamoDB blood_glucose_record 에서 24h 시계열 조회 (timestamp 오름차순 level 추출)."""
    user_date = f"{user_id}#{date}"
    response = _state["dynamodb_table"].query(
        KeyConditionExpression=Key("user_date").eq(user_date),
    )
    items = response.get("Items", [])
    # timestamp 오름차순 정렬
    items.sort(key=lambda x: x.get("timestamp", ""))
    levels = [float(item["level"]) for item in items if "level" in item]

    if len(levels) < required_len:
        raise HTTPException(
            status_code=422,
            detail=(
                f"혈당 데이터가 {required_len} step에 미달합니다 "
                f"(user_date={user_date}, 실제={len(levels)} step). "
                f"24시간 분량(5분 간격 288 측정값)이 필요합니다."
            ),
        )
    # 마지막 required_len 개만 사용
    return levels[-required_len:]


@app.post("/predict/blood-glucose/{user_id}", response_model=PredictResponse)
def predict_blood_glucose(
    user_id: str,
    date: str,
    body: Optional[PredictRequest] = None,
) -> PredictResponse:
    metadata = _state["metadata"]
    model: Seq2SeqGRU = _state["model"]
    scaler = _state["scaler"]
    input_len = metadata["input_len"]
    output_len = metadata["output_len"]

    if body is not None and body.glucose:
        if len(body.glucose) != input_len:
            raise HTTPException(
                status_code=422,
                detail=f"body.glucose 길이가 {input_len} 이 아닙니다 (실제={len(body.glucose)}).",
            )
        glucose_raw = body.glucose
        source = "body"
    else:
        glucose_raw = _fetch_glucose_from_db(user_id, date, input_len)
        source = "db"

    # 정규화 → tensor (1, input_len, 1)
    arr = np.array(glucose_raw, dtype=np.float32).reshape(-1, 1)
    arr_scaled = scaler.transform(arr).reshape(1, input_len, 1)
    src = torch.tensor(arr_scaled, dtype=torch.float32)

    with torch.no_grad():
        pred_scaled = model(src, target=None, teacher_forcing_ratio=0.0)

    # inverse_transform → list[float]
    pred_arr = pred_scaled.cpu().numpy().reshape(-1, 1)
    pred_original = scaler.inverse_transform(pred_arr).reshape(-1)

    return PredictResponse(
        predictions=[float(v) for v in pred_original],
        horizon_minutes=metadata["horizon_minutes"],
        source=source,
    )
