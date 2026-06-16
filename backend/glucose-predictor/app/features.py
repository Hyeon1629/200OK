"""혈당예측 7피처 입력 전처리 빌더 (basal 제외).

train.py main() 158~181행 + make_sliding_windows(= lsy `glucose model.ipynb` cell-6)의
전처리를 **추론용으로 이관·재현**한다. 입력이 학습 df가 아니라 5분 그리드(288)이고 basal만 제거.

7피처 순서(train.py FEATURE_COLS에서 basal 제외):
    [gl_scaled, gl_diff, gl_accel, hour_sin, hour_cos, carbs, bolus]

- gl_scaled : 혈당을 5분 그리드로 선형 보간 후 scaler.transform (scaler 없으면 raw gl)
- gl_diff   : gl_scaled.diff()   (※ 반드시 스케일 후 diff — train.py와 일치)
- gl_accel  : gl_diff.diff()
- hour_sin/cos : 각 5분 스텝 timestamp 의 hour(=h+m/60)
- carbs/bolus  : 이벤트(시각,값)를 5분 그리드 스텝에 정렬(해당 버킷 합산, 그 외 0)

⚠️ 모델 미연결 단계 — 이 모듈은 (288,7) 행렬을 만들어내는 것까지만. 실제 모델 추론/scaler는
   7피처 모델 연결 시 주입한다. 학습/추론 전처리 1:1 일치가 검증 기준.
"""

from __future__ import annotations

from datetime import datetime
from typing import Optional, Sequence

import numpy as np

INPUT_LEN = 288
STEP_SECONDS = 5 * 60  # 5분
FEATURE_COLS = ["gl_scaled", "gl_diff", "gl_accel", "hour_sin", "hour_cos", "carbs", "bolus"]

# 입력 품질 가드 (main.py와 동일 기준)
MIN_READINGS = 6
MIN_COVERAGE_HOURS = 12


class InsufficientDataError(ValueError):
    """혈당 측정이 부족해 7피처를 구성할 수 없음 (호출부에서 422 매핑)."""


def _dedup_sorted(points: Sequence[tuple[datetime, float]]) -> list[tuple[datetime, float]]:
    grouped: dict[datetime, list[float]] = {}
    for ts, v in points:
        grouped.setdefault(ts, []).append(float(v))
    return sorted((ts, sum(vs) / len(vs)) for ts, vs in grouped.items())


def _grid_epochs(end_ts: float, n: int) -> np.ndarray:
    """end_ts(초)에서 직전 n점, 5분 간격 그리드(오름차순 epoch초)."""
    return np.array([end_ts - (n - 1 - i) * STEP_SECONDS for i in range(n)], dtype=np.float64)


def _align_events_to_grid(events: Sequence[tuple[datetime, float]], grid_epochs: np.ndarray) -> np.ndarray:
    """이벤트(시각,값)를 5분 버킷에 합산 정렬. 그리드 밖/빈 스텝은 0.

    각 그리드 스텝 t 는 (t-STEP, t] 구간을 대표한다고 보고 그 안의 이벤트 값을 합산.
    """
    out = np.zeros(len(grid_epochs), dtype=np.float64)
    if not events:
        return out
    start = grid_epochs[0] - STEP_SECONDS
    for ts, val in events:
        e = ts.timestamp()
        if e <= start or e > grid_epochs[-1]:
            continue
        # (t-STEP, t] 버킷 → ceil 위치
        idx = int(np.ceil((e - grid_epochs[0]) / STEP_SECONDS))
        idx = max(0, min(len(out) - 1, idx))
        out[idx] += float(val)
    return out


def build_features(
    glucose: Sequence[tuple[datetime, float]],
    carbs: Sequence[tuple[datetime, float]] = (),
    bolus: Sequence[tuple[datetime, float]] = (),
    *,
    input_len: int = INPUT_LEN,
    scaler=None,
) -> np.ndarray:
    """운영 데이터 → (input_len, 7) 7피처 행렬.

    glucose: [(datetime, mg/dL), ...] (불규칙 가능 — 5분 그리드로 보간)
    carbs  : [(datetime, grams), ...]  (diet)
    bolus  : [(datetime, units), ...]  (insulin RAPID)
    scaler : sklearn StandardScaler(gl 기준). None이면 raw gl로 대체(모델 연결 시 주입).
    """
    pts = _dedup_sorted(glucose)
    if len(pts) < MIN_READINGS:
        raise InsufficientDataError(
            f"혈당 측정 부족: 최소 {MIN_READINGS}회 필요, 실제 {len(pts)}회"
        )
    span_h = (pts[-1][0] - pts[0][0]).total_seconds() / 3600.0
    if span_h < MIN_COVERAGE_HOURS:
        raise InsufficientDataError(
            f"혈당 구간 짧음: 최소 {MIN_COVERAGE_HOURS}시간 필요, 실제 {span_h:.1f}시간"
        )

    end_ts = pts[-1][0].timestamp()
    grid = _grid_epochs(end_ts, input_len)

    # gl 보간
    xk = np.array([p[0].timestamp() for p in pts], dtype=np.float64)
    yk = np.array([p[1] for p in pts], dtype=np.float64)
    gl = np.interp(grid, xk, yk)

    # gl_scaled (scaler 있으면 적용, 없으면 raw) → diff → accel (train.py 순서)
    if scaler is not None:
        gl_scaled = scaler.transform(gl.reshape(-1, 1)).reshape(-1)
    else:
        gl_scaled = gl.astype(np.float64)
    gl_diff = np.diff(gl_scaled, prepend=gl_scaled[0])   # 첫 스텝 diff=0 (fillna(0)와 동일 효과)
    gl_diff[0] = 0.0
    gl_accel = np.diff(gl_diff, prepend=gl_diff[0])
    gl_accel[0] = 0.0

    # hour_sin/cos (각 그리드 스텝 시각)
    hours = np.array(
        [(lambda d: d.hour + d.minute / 60.0)(datetime.fromtimestamp(t)) for t in grid],
        dtype=np.float64,
    )
    hour_sin = np.sin(2 * np.pi * hours / 24.0)
    hour_cos = np.cos(2 * np.pi * hours / 24.0)

    # carbs / bolus 그리드 정렬
    carbs_arr = _align_events_to_grid(carbs, grid)
    bolus_arr = _align_events_to_grid(bolus, grid)

    feats = np.stack(
        [gl_scaled, gl_diff, gl_accel, hour_sin, hour_cos, carbs_arr, bolus_arr],
        axis=1,
    ).astype(np.float32)
    assert feats.shape == (input_len, len(FEATURE_COLS))
    return feats
