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
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional

import boto3
import numpy as np
import torch
from boto3.dynamodb.conditions import Key
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.model import Seq2SeqGRU, DirectMultistepGRU
from app.features import build_features, InsufficientDataError


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
    scaler_path = ARTIFACTS_DIR / "scaler.pkl"

    with metadata_path.open() as f:
        metadata = json.load(f)
    _state["metadata"] = metadata

    # 가중치 파일명은 metadata 로 지정(v2=best.pt, v1=best_seq2seq_gru.pt)
    pt_path = ARTIFACTS_DIR / metadata.get("weights_file", "best_seq2seq_gru.pt")
    if not metadata_path.exists() or not pt_path.exists() or not scaler_path.exists():
        raise RuntimeError(f"artifacts 누락 — {ARTIFACTS_DIR} 확인 필요")

    arch = metadata.get("arch", "seq2seq")
    if arch == "direct_multistep":
        model = DirectMultistepGRU(
            input_dim=metadata["encoder_input_dim"],
            hidden_dim=metadata["hidden_dim"],
            num_layers=metadata["num_layers"],
            output_len=metadata["output_len"],
            dropout=metadata["dropout"],
        )
    else:  # v1 단변량 Seq2Seq (롤백 경로)
        model = Seq2SeqGRU(
            encoder_input_dim=metadata.get("encoder_input_dim", 1),
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


class Event(BaseModel):
    timestamp: str   # ISO-8601
    value: float


class PredictRequest(BaseModel):
    # v2(7피처, 아키텍처 A): Spring 이 RDS 에서 모아 보내는 carbs/bolus 이벤트.
    carbs: list[Event] = []
    bolus: list[Event] = []
    # v1 호환: glucose 288 직접 전달(있으면 사용)
    glucose: Optional[list[float]] = None


class PredictResponse(BaseModel):
    predictions: list[float]
    horizon_minutes: int
    source: str  # "body" | "db_interp" | "db+events"


# 보간 입력 품질 가드 — 측정이 너무 적거나 구간이 짧으면 거짓 예측 방지 위해 거부
MIN_READINGS = 6
MIN_COVERAGE_HOURS = 12
STEP_SECONDS = 5 * 60  # 모델 입력 5분 간격


def _parse_ts(value) -> Optional[datetime]:
    try:
        dt = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        return dt.replace(tzinfo=None)  # naive 통일 (상대 간격만 사용)
    except Exception:
        return None


def _query_levels(user_id: str, date: str) -> list[tuple[datetime, float]]:
    """해당 날짜의 (timestamp, level) 목록."""
    response = _state["dynamodb_table"].query(
        KeyConditionExpression=Key("user_date").eq(f"{user_id}#{date}"),
    )
    out: list[tuple[datetime, float]] = []
    for item in response.get("Items", []):
        if "level" not in item or "timestamp" not in item:
            continue
        ts = _parse_ts(item["timestamp"])
        if ts is None:
            continue
        try:
            out.append((ts, float(item["level"])))
        except (TypeError, ValueError):
            continue
    return out


def _fetch_glucose_from_db(user_id: str, date: str, required_len: int) -> list[float]:
    """blood_glucose_record(date + 전날)를 읽어 최신 측정 기준 직전 24h를 5분 그리드로 선형 보간.

    운영 혈당은 1시간 주기·수동 입력이라 그대로는 288개(5분×24h)가 안 모인다.
    따라서 시간축 선형 보간(numpy.interp)으로 required_len 개 입력을 구성한다.
    ⚠️ 보간값은 저장하지 않으며(메모리 1회용), 실제 5분 변동이 아니라 예측 신뢰도는 제한적.
    품질 가드: 측정 < MIN_READINGS 또는 구간 < MIN_COVERAGE_HOURS 면 422.
    """
    prev_date = (datetime.fromisoformat(date) - timedelta(days=1)).date().isoformat()
    readings = _query_levels(user_id, prev_date) + _query_levels(user_id, date)

    # 동일 timestamp 평균으로 중복 제거 + 시간순 정렬
    grouped: dict[datetime, list[float]] = {}
    for ts, lv in readings:
        grouped.setdefault(ts, []).append(lv)
    pts = sorted((ts, sum(v) / len(v)) for ts, v in grouped.items())

    if len(pts) < MIN_READINGS:
        raise HTTPException(
            status_code=422,
            detail=(
                f"예측에 필요한 혈당 측정이 부족합니다 "
                f"(최소 {MIN_READINGS}회 필요, 실제 {len(pts)}회). 혈당을 더 기록해 주세요."
            ),
        )
    span_hours = (pts[-1][0] - pts[0][0]).total_seconds() / 3600.0
    if span_hours < MIN_COVERAGE_HOURS:
        raise HTTPException(
            status_code=422,
            detail=(
                f"혈당 기록 구간이 짧습니다 "
                f"(최소 {MIN_COVERAGE_HOURS}시간 필요, 실제 {span_hours:.1f}시간)."
            ),
        )

    # 5분 그리드: 최신 측정 시각에서 직전 24h (required_len 점). 범위 밖은 양끝 값으로 clamp.
    end = pts[-1][0].timestamp()
    x_target = np.array(
        [end - (required_len - 1 - i) * STEP_SECONDS for i in range(required_len)],
        dtype=np.float64,
    )
    x_known = np.array([p[0].timestamp() for p in pts], dtype=np.float64)
    y_known = np.array([p[1] for p in pts], dtype=np.float64)
    levels = np.interp(x_target, x_known, y_known)
    return [float(v) for v in levels]


def _events(items) -> list[tuple[datetime, float]]:
    """[Event] → [(datetime, value)] (timestamp 파싱 실패분 제외)."""
    out: list[tuple[datetime, float]] = []
    for e in items or []:
        ts = _parse_ts(e.timestamp)
        if ts is None:
            continue
        try:
            out.append((ts, float(e.value)))
        except (TypeError, ValueError):
            continue
    return out


@app.post("/predict/blood-glucose/{user_id}", response_model=PredictResponse)
def predict_blood_glucose(
    user_id: str,
    date: str,
    body: Optional[PredictRequest] = None,
) -> PredictResponse:
    metadata = _state["metadata"]
    model = _state["model"]
    scaler = _state["scaler"]
    input_len = metadata["input_len"]
    arch = metadata.get("arch", "seq2seq")

    if arch == "direct_multistep":
        # 7피처: glucose=DynamoDB 자체조회, carbs/bolus=요청 body(아키텍처 A).
        prev_date = (datetime.fromisoformat(date) - timedelta(days=1)).date().isoformat()
        glucose_pts = _query_levels(user_id, prev_date) + _query_levels(user_id, date)
        carbs = _events(body.carbs) if body is not None else []
        bolus = _events(body.bolus) if body is not None else []
        try:
            feats = build_features(glucose_pts, carbs, bolus, input_len=input_len, scaler=scaler)
        except InsufficientDataError as e:
            raise HTTPException(status_code=422, detail=str(e))
        src = torch.tensor(feats, dtype=torch.float32).unsqueeze(0)  # (1, input_len, 7)
        with torch.no_grad():
            pred_scaled = model(src)
        source = "db+events"
    else:
        # v1 단변량 Seq2Seq 경로 (롤백 호환)
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
            source = "db_interp"
        arr = np.array(glucose_raw, dtype=np.float32).reshape(-1, 1)
        arr_scaled = scaler.transform(arr).reshape(1, input_len, 1)
        src = torch.tensor(arr_scaled, dtype=torch.float32)
        with torch.no_grad():
            pred_scaled = model(src, target=None, teacher_forcing_ratio=0.0)

    pred_arr = pred_scaled.cpu().numpy().reshape(-1, 1)
    pred_original = scaler.inverse_transform(pred_arr).reshape(-1)
    return PredictResponse(
        predictions=[float(v) for v in pred_original],
        horizon_minutes=metadata["horizon_minutes"],
        source=source,
    )
