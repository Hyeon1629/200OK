"""
train_skforecast_dependent_multivariate.py

AZT1D CGM 예측을 위한 Skforecast 종속 다변량 예측 스크립트입니다.

목표:
    피험자별로 서로 관련된 여러 시계열을 사용합니다:
        CGM, basal, bolus, carbs, insulin
    예측 대상은 다음 혈당 값입니다:
        CGM
    예측 구간은 향후 3시간입니다:
        데이터 간격이 5분이면 36개 시점입니다.

이 스크립트는 다음 Skforecast 구성을 따릅니다:
    ForecasterDirectMultiVariate + level='CGM' + steps=36

필요 패키지 설치:
    pip install pandas numpy matplotlib scikit-learn lightgbm skforecast

실행 예시:
    python train_skforecast_dependent_multivariate.py --csv "AZT1D(1).csv"

참고:
    - 피험자별로 모델을 하나씩 학습합니다.
    - CGM, basal, bolus, carbs, insulin의 과거 값만 입력으로 사용합니다.
    - 미래 carbs/bolus/insulin 값은 입력으로 사용하지 않습니다.
    - 각 피험자의 학습 구간 직후 3시간 예측 구간 하나를 예측합니다.
"""

import argparse
import os
import warnings
from typing import List, Optional, Tuple

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

# 그래프에 한글이 깨지지 않도록 윈도우 기본 한글 폰트를 사용합니다.
plt.rcParams["font.family"] = "Malgun Gothic"
plt.rcParams["axes.unicode_minus"] = False

try:
    from lightgbm import LGBMRegressor
except ImportError as exc:
    raise ImportError(
        "lightgbm is not installed. Install it with: pip install lightgbm"
    ) from exc

try:
    from skforecast.direct import ForecasterDirectMultiVariate
    from skforecast.preprocessing import RollingFeatures
except ImportError as exc:
    raise ImportError(
        "skforecast is not installed. Install it with: pip install skforecast"
    ) from exc


warnings.filterwarnings("ignore")


SERIES_COLS = ["CGM", "basal", "bolus", "carbs", "insulin"]


def parse_lags(lags_str: str) -> List[int]:
    """쉼표로 구분된 lag 목록을 파싱합니다. 예: '1,2,3,6,12'."""
    return [int(x.strip()) for x in lags_str.split(",") if x.strip()]


def load_data(csv_path: str) -> pd.DataFrame:
    
    df = pd.read_csv(csv_path)

    required = ["id", "date"] + SERIES_COLS
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(f"Missing required columns: {missing}")

    df = df[required].copy()
    df["date"] = pd.to_datetime(df["date"], errors="coerce")
    df = df.dropna(subset=["id", "date", "CGM"])
    df = df.sort_values(["id", "date"]).reset_index(drop=True)

    for col in ["basal", "bolus", "carbs", "insulin"]:
        df[col] = df[col].fillna(0)

    return df


def prepare_subject_series(
    df: pd.DataFrame,
    subject_id,
    freq: str = "5min",
) -> pd.DataFrame:
    """
    한 피험자의 데이터를 DatetimeIndex 기반 다변량 시계열로 준비합니다.

    출력 컬럼:
        CGM, basal, bolus, carbs, insulin
    """
    data = (
        df[df["id"] == subject_id]
        .sort_values("date")
        [["date"] + SERIES_COLS]
        .copy()
    )

    data = data.dropna(subset=["CGM"])

    for col in ["basal", "bolus", "carbs", "insulin"]:
        data[col] = data[col].fillna(0)

    data = data.set_index("date").sort_index()

    # 중복된 타임스탬프가 있으면 첫 번째 값만 남깁니다.
    data = data[~data.index.duplicated(keep="first")]

    # 5분 간격의 규칙적인 시간 격자로 맞춥니다.
    data = data.asfreq(freq)

    data["CGM"] = data["CGM"].interpolate(method="time", limit_direction="both")

    # 이벤트/처치 변수는 결측이면 기록된 이벤트나 처치가 없다고 보고 0으로 채웁니다.
    for col in ["basal", "bolus", "carbs", "insulin"]:
        data[col] = data[col].fillna(0)

    return data


def build_forecaster(
    steps: int,
    lags: List[int],
    n_estimators: int,
    learning_rate: float,
    max_depth: int,
    random_state: int,
    use_rolling: bool = True,
):
    """Skforecast ForecasterDirectMultiVariate 모델을 생성합니다."""
    regressor = LGBMRegressor(
        n_estimators=n_estimators,
        learning_rate=learning_rate,
        max_depth=max_depth,
        random_state=random_state,
        verbose=-1,
    )

    if use_rolling:
        window_features = RollingFeatures(
            stats=["mean", "std"],
            window_sizes=[6, 12, 36],  # 5분 간격 기준 30분, 1시간, 3시간
        )
    else:
        window_features = None

    forecaster = ForecasterDirectMultiVariate(
        regressor=regressor,
        level="CGM",
        steps=steps,
        lags=lags,
        window_features=window_features,
        n_jobs="auto",
    )

    return forecaster


def evaluate_prediction(actual: pd.Series, pred: pd.Series) -> Tuple[float, float, float]:
    """MAE, RMSE, R2를 반환합니다."""
    mae = mean_absolute_error(actual, pred)
    rmse = np.sqrt(mean_squared_error(actual, pred))
    r2 = r2_score(actual, pred)
    return mae, rmse, r2


def plot_prediction(
    actual: pd.Series,
    pred: pd.Series,
    subject_id,
    output_dir: str,
):
    """3시간 예측 결과 그래프"""
    plt.figure(figsize=(12, 4))
    plt.plot(actual.index, actual.values, marker="o", label="실제 CGM")
    plt.plot(pred.index, pred.values, marker="o", label="예측 CGM")
    plt.xlabel("시간")
    plt.ylabel("CGM")
    plt.title(f"Skforecast 종속 다변량 CGM 예측 - 피험자 {subject_id}")
    plt.legend()
    plt.tight_layout()

    path = os.path.join(output_dir, f"subject_{subject_id}_prediction.png")
    plt.savefig(path, dpi=150)
    plt.close()


def train_one_subject(
    df: pd.DataFrame,
    subject_id,
    args,
    lags: List[int],
) -> Optional[dict]:
    """한 피험자에 대해 모델을 학습하고 평가합니다."""
    data = prepare_subject_series(df, subject_id, freq=args.freq)

    min_required = max(lags) + args.steps + args.min_extra_rows
    if len(data) < min_required:
        print(f"[SKIP] Subject {subject_id}: not enough rows ({len(data)} < {min_required})")
        return None

    split_idx = int(len(data) * args.train_ratio)
    data_train = data.iloc[:split_idx].copy()
    data_test = data.iloc[split_idx:].copy()

    # 3시간 예측 평가를 하려면 테스트 구간에 최소 예측 시점 수만큼의 행 필요.
    if len(data_test) < args.steps:
        print(f"[SKIP] Subject {subject_id}: test too short ({len(data_test)} < {args.steps})")
        return None

    forecaster = build_forecaster(
        steps=args.steps,
        lags=lags,
        n_estimators=args.n_estimators,
        learning_rate=args.learning_rate,
        max_depth=args.max_depth,
        random_state=args.random_state,
        use_rolling=not args.no_rolling,
    )

    print(f"[FIT] Subject {subject_id}: train={data_train.shape}, test={data_test.shape}")
    forecaster.fit(series=data_train)

    # 학습 구간이 끝난 직후의 다음 36개 시점 예측.
    pred = forecaster.predict(steps=args.steps)

    # 테스트 구간의 실제 첫 36개 CGM 값을 예측 인덱스에 맞춰 정렬.
    actual = data_test["CGM"].loc[pred.index]

    mae, rmse, r2 = evaluate_prediction(actual, pred)

    # 예측 결과 행을 저장.
    pred_df = pd.DataFrame({
        "id": subject_id,
        "date": pred.index,
        "actual_CGM": actual.values,
        "pred_CGM": pred.values,
        "horizon_step": np.arange(1, len(pred) + 1),
        "minutes_ahead": np.arange(1, len(pred) + 1) * 5,
    })
    pred_path = os.path.join(args.output_dir, f"subject_{subject_id}_prediction.csv")
    pred_df.to_csv(pred_path, index=False)

    if args.save_plots:
        plot_prediction(actual, pred, subject_id, args.output_dir)

    print(
        f"[DONE] Subject {subject_id}: "
        f"MAE={mae:.3f}, RMSE={rmse:.3f}, R2={r2:.3f}"
    )

    return {
        "id": subject_id,
        "MAE": mae,
        "RMSE": rmse,
        "R2": r2,
        "n_total": len(data),
        "n_train": len(data_train),
        "n_test": len(data_test),
        "train_start": data_train.index.min(),
        "train_end": data_train.index.max(),
        "test_start": data_test.index.min(),
        "test_end": data_test.index.max(),
    }


def main():
    parser = argparse.ArgumentParser(
        description="Skforecast dependent multivariate CGM forecasting for AZT1D."
    )

    parser.add_argument("--csv", type=str, required=True, help="Path to AZT1D csv file.")
    parser.add_argument("--output_dir", type=str, default="outputs_skforecast_dependent_multivariate")
    parser.add_argument("--subject", type=str, default="all", help="Subject id to train, or 'all'.")

    parser.add_argument("--freq", type=str, default="5min", help="Time frequency, default 5min.")
    parser.add_argument("--steps", type=int, default=36, help="Forecast steps. 36 = 3 hours for 5-min data.")
    parser.add_argument("--train_ratio", type=float, default=0.8)

    parser.add_argument(
        "--lags",
        type=str,
        default="1,2,3,6,12,24,36,72,144,288",
        help="Comma-separated lag list. Example: 1,2,3,6,12,24,36,72,144,288",
    )

    parser.add_argument("--n_estimators", type=int, default=300)
    parser.add_argument("--learning_rate", type=float, default=0.03)
    parser.add_argument("--max_depth", type=int, default=4)
    parser.add_argument("--random_state", type=int, default=42)

    parser.add_argument("--min_extra_rows", type=int, default=100)
    parser.add_argument("--no_rolling", action="store_true", help="Disable RollingFeatures.")
    parser.add_argument("--save_plots", action="store_true", help="Save prediction plots per subject.")

    args = parser.parse_args()
    os.makedirs(args.output_dir, exist_ok=True)

    lags = parse_lags(args.lags)
    print("Lags:", lags)
    print("Steps:", args.steps)

    df = load_data(args.csv)
    subjects_all = sorted(df["id"].dropna().unique())

    if args.subject.lower() == "all":
        subjects = subjects_all
    else:
        # 가능하면 정수로 유지하고, 변환할 수 없으면 문자열로 사용합니다.
        try:
            subject_value = int(args.subject)
        except ValueError:
            subject_value = args.subject
        subjects = [subject_value]

    print("Subjects to train:", subjects)

    results = []

    for subject_id in subjects:
        try:
            result = train_one_subject(df, subject_id, args, lags)
            if result is not None:
                results.append(result)
        except Exception as e:
            print(f"[ERROR] Subject {subject_id}: {e}")
            results.append({
                "id": subject_id,
                "MAE": np.nan,
                "RMSE": np.nan,
                "R2": np.nan,
                "error": str(e),
            })

    results_df = pd.DataFrame(results)
    results_path = os.path.join(args.output_dir, "subject_metrics.csv")
    results_df.to_csv(results_path, index=False)

    print("\nSaved subject metrics to:", results_path)
    print(results_df)

    if len(results_df) > 0 and "MAE" in results_df:
        valid = results_df.dropna(subset=["MAE"])
        if len(valid) > 0:
            overall = pd.DataFrame([{
                "mean_MAE": valid["MAE"].mean(),
                "mean_RMSE": valid["RMSE"].mean(),
                "mean_R2": valid["R2"].mean(),
                "n_subjects": len(valid),
            }])
            overall_path = os.path.join(args.output_dir, "overall_metrics.csv")
            overall.to_csv(overall_path, index=False)
            print("\nOverall metrics:")
            print(overall)
            print("Saved overall metrics to:", overall_path)

