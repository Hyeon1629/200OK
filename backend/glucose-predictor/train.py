"""Seq2Seq GRU 혈당 예측 모델 학습 스크립트 (v2 — 다변량 8피처).

팀원(lsy)의 glucose_model.py 노트북을 인프라 어댑터만 정정해 .py로 추출.
모델·학습 로직 100% 동일. 변경 사항:
  - 하드코딩 csv_path 제거 → --csv path 인자
  - SMALL_DATA_TEST / selected_ids[:25] 분기 제거 → --n-users 인자 (default 25)
  - EPOCHS 변수 정의부에서 인자로 (default 15)
  - matplotlib 시각화 셀 제거 (학습/평가에 불필요)
  - 학습 끝나면 artifacts/ 에 state_dict + scaler + metadata 저장

데이터셋: AZT1D.csv (Dexcom G6, 5분 간격). 컬럼 date→time, CGM→gl + carbs/bolus/basal.

사용:
    python train.py --csv ~/Downloads/AZT1D.csv --n-users 25 --epochs 15
"""

from __future__ import annotations

import argparse
import json
import pickle
import random
from pathlib import Path

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from torch.utils.data import DataLoader, Dataset

from app.model import Seq2SeqGRU


# ── 노트북 변수 정의부 그대로 ───────────────────────────────────────────────
SEED = 42
INPUT_LEN = 288
OUTPUT_LEN = 36
BATCH_SIZE = 64
LR = 1e-3
STRIDE = 3  # 노트북: 15분마다 window 생성 (메모리 절감)

# 노트북 cell 의 8개 입력 피처 (순서 고정 — 추론에서 동일 순서로 재구성해야 함)
FEATURE_COLS = [
    "gl_scaled",
    "gl_diff",
    "gl_accel",
    "hour_sin",
    "hour_cos",
    "carbs",
    "bolus",
    "basal",
]


# ── 노트북 Dataset ──────────────────────────────────────────────────────────
class CGMDataset(Dataset):
    def __init__(self, X, y):
        self.X = torch.tensor(X, dtype=torch.float32)
        self.y = torch.tensor(y, dtype=torch.float32)

    def __len__(self):
        return len(self.X)

    def __getitem__(self, idx):
        return self.X[idx], self.y[idx]


# ── 노트북 sliding window 그대로 (다변량 + stride) ──────────────────────────
def make_sliding_windows(df, input_len=288, output_len=36, feature_cols=None, stride=3):
    X_list = []
    y_list = []

    if feature_cols is None:
        feature_cols = ["gl_scaled"]

    for pid, group in df.groupby("id"):
        group = group.sort_values("time")

        features = group[feature_cols].to_numpy(dtype=np.float32)
        target = group["gl_scaled"].to_numpy(dtype=np.float32)

        if len(group) < input_len + output_len:
            continue

        for i in range(0, len(group) - input_len - output_len + 1, stride):
            x = features[i : i + input_len]
            y = target[i + input_len : i + input_len + output_len]
            X_list.append(x)
            y_list.append(y)

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.float32)
    return X, y


# ── 노트북 학습 1 epoch ─────────────────────────────────────────────────────
def train_one_epoch(model, loader, criterion, optimizer, device):
    model.train()
    total_loss = 0.0
    for X_batch, y_batch in loader:
        X_batch = X_batch.to(device)
        y_batch = y_batch.to(device)
        optimizer.zero_grad()
        pred = model(X_batch, target=y_batch, teacher_forcing_ratio=0.5)
        loss = criterion(pred, y_batch)
        loss.backward()
        optimizer.step()
        total_loss += loss.item() * X_batch.size(0)
    return total_loss / len(loader.dataset)


# ── 노트북 평가 ─────────────────────────────────────────────────────────────
def evaluate(model, loader, criterion, device):
    model.eval()
    total_loss = 0.0
    with torch.no_grad():
        for X_batch, y_batch in loader:
            X_batch = X_batch.to(device)
            y_batch = y_batch.to(device)
            pred = model(X_batch, target=None, teacher_forcing_ratio=0.0)
            loss = criterion(pred, y_batch)
            total_loss += loss.item() * X_batch.size(0)
    return total_loss / len(loader.dataset)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", required=True, help="AZT1D.csv path")
    parser.add_argument("--n-users", type=int, default=25,
                        help="처음 N명만 사용 (노트북의 SMALL_DATA_TEST 대체). 0이면 전체")
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--output-dir", default="artifacts")
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    # ── seed 고정 (노트북) ──────────────────────────────────────────────────
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    # ── 데이터 로드 + N명 추출 (노트북) ─────────────────────────────────────
    df = pd.read_csv(args.csv)
    if args.n_users and args.n_users > 0:
        selected_ids = df["id"].unique()[: args.n_users]
        df = df[df["id"].isin(selected_ids)].copy()
        print(f"selected IDs: {selected_ids}")
    else:
        print("full dataset mode")
    print(f"df shape: {df.shape}")

    # ── 전처리 (노트북 그대로) ──────────────────────────────────────────────
    df = df.rename(columns={"date": "time", "CGM": "gl"})
    df["time"] = pd.to_datetime(df["time"])

    df = df[["id", "time", "gl", "carbs", "bolus", "basal"]].copy()
    df = df.dropna(subset=["id", "time", "gl"])

    df["carbs"] = df["carbs"].fillna(0)
    df["bolus"] = df["bolus"].fillna(0)
    df["basal"] = df.groupby("id")["basal"].ffill().fillna(0)

    df = df.sort_values(["id", "time"]).reset_index(drop=True)

    # glucose 정규화 (gl 단일 컬럼 기준 — 추론도 동일)
    scaler = StandardScaler()
    df["gl_scaled"] = scaler.fit_transform(df[["gl"]])

    # 시간대 feature
    df["hour"] = df["time"].dt.hour + df["time"].dt.minute / 60
    df["hour_sin"] = np.sin(2 * np.pi * df["hour"] / 24)
    df["hour_cos"] = np.cos(2 * np.pi * df["hour"] / 24)

    # 혈당 변화량/꺾임
    df["gl_diff"] = df.groupby("id")["gl_scaled"].diff().fillna(0)
    df["gl_accel"] = df.groupby("id")["gl_diff"].diff().fillna(0)

    print(f"사용 feature: {FEATURE_COLS}")

    # ── sliding window (노트북) ─────────────────────────────────────────────
    X, y = make_sliding_windows(
        df, input_len=INPUT_LEN, output_len=OUTPUT_LEN,
        feature_cols=FEATURE_COLS, stride=STRIDE,
    )
    print(f"X shape: {X.shape}, y shape: {y.shape}")

    # ── train/val/test split (노트북) ───────────────────────────────────────
    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.3, random_state=args.seed, shuffle=True,
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.5, random_state=args.seed, shuffle=True,
    )

    # ── DataLoader (노트북) ─────────────────────────────────────────────────
    train_loader = DataLoader(CGMDataset(X_train, y_train),
                              batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(CGMDataset(X_val, y_val),
                            batch_size=BATCH_SIZE, shuffle=False)
    test_loader = DataLoader(CGMDataset(X_test, y_test),
                             batch_size=BATCH_SIZE, shuffle=False)

    # ── 모델 (노트북 그대로 — 인코더 8피처, 디코더 1채널) ───────────────────
    model = Seq2SeqGRU(
        encoder_input_dim=len(FEATURE_COLS), decoder_input_dim=1,
        hidden_dim=64, num_layers=2, output_len=OUTPUT_LEN, dropout=0.2,
    ).to(device)

    criterion = nn.MSELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=LR)

    # ── 학습 (노트북) ───────────────────────────────────────────────────────
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    best_model_path = output_dir / "best_seq2seq_gru.pt"
    best_val_loss = float("inf")

    for epoch in range(1, args.epochs + 1):
        train_loss = train_one_epoch(model, train_loader, criterion, optimizer, device)
        val_loss = evaluate(model, val_loader, criterion, device)
        print(f"Epoch [{epoch}/{args.epochs}] "
              f"Train Loss: {train_loss:.6f}  Val Loss: {val_loss:.6f}")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            torch.save(model.state_dict(), best_model_path)
            print("  Best model saved.")

    # ── 테스트 평가 (노트북) ────────────────────────────────────────────────
    model.load_state_dict(torch.load(best_model_path, map_location=device))
    model.eval()

    all_preds, all_targets = [], []
    with torch.no_grad():
        for X_batch, y_batch in test_loader:
            X_batch = X_batch.to(device)
            pred = model(X_batch, target=None, teacher_forcing_ratio=0.0)
            all_preds.append(pred.cpu().numpy())
            all_targets.append(y_batch.numpy())

    all_preds = np.concatenate(all_preds, axis=0)
    all_targets = np.concatenate(all_targets, axis=0)

    preds_original = scaler.inverse_transform(
        all_preds.reshape(-1, 1)
    ).reshape(all_preds.shape)
    targets_original = scaler.inverse_transform(
        all_targets.reshape(-1, 1)
    ).reshape(all_targets.shape)

    mae = mean_absolute_error(targets_original.reshape(-1),
                              preds_original.reshape(-1))
    rmse = np.sqrt(mean_squared_error(targets_original.reshape(-1),
                                      preds_original.reshape(-1)))
    print(f"\nTest MAE:  {mae:.2f} mg/dL")
    print(f"Test RMSE: {rmse:.2f} mg/dL")
    print(f"(참고 v1: weinstock 20명/5epoch MAE 36.96, RMSE 52.65)")

    # ── 산출물 저장 ─────────────────────────────────────────────────────────
    with open(output_dir / "scaler.pkl", "wb") as f:
        pickle.dump(scaler, f)

    metadata = {
        "input_len": INPUT_LEN,
        "output_len": OUTPUT_LEN,
        "encoder_input_dim": len(FEATURE_COLS),
        "decoder_input_dim": 1,
        "feature_cols": FEATURE_COLS,
        "hidden_dim": 64,
        "num_layers": 2,
        "dropout": 0.2,
        "stride": STRIDE,
        "horizon_minutes": OUTPUT_LEN * 5,
        "dataset": "AZT1D",
        "n_users": args.n_users,
        "epochs": args.epochs,
        "test_mae_mg_dl": float(mae),
        "test_rmse_mg_dl": float(rmse),
    }
    with open(output_dir / "metadata.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

    print(f"\nsaved → {output_dir.resolve()}")
    print(f"  best_seq2seq_gru.pt  ({best_model_path.stat().st_size / 1024:.1f} KB)")
    print(f"  scaler.pkl")
    print(f"  metadata.json")


if __name__ == "__main__":
    main()
