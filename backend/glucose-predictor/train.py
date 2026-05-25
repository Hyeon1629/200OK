"""Seq2Seq GRU 혈당 예측 모델 학습 스크립트.

팀원의 glucose_model_1.ipynb 를 인프라 어댑터만 정정해 .py로 추출.
모델·학습 로직 100% 동일. 변경 사항:
  - Colab files.upload() 제거 → --csv path 인자
  - SMALL_DATA_TEST 분기 제거 → --n-users 인자 (default 20)
  - EPOCHS 변수 정의부에서 인자로 (default 5)
  - 학습 끝나면 artifacts/ 에 state_dict + scaler + metadata 저장

사용:
    python train.py --csv ~/Downloads/weinstock.csv --n-users 20 --epochs 5
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


# ── 노트북 cell 1 변수 정의부 그대로 ─────────────────────────────────────────
SEED = 42
INPUT_LEN = 288
OUTPUT_LEN = 36
BATCH_SIZE = 64
LR = 1e-3


# ── 노트북 cell 13 Dataset ─────────────────────────────────────────────────
class CGMDataset(Dataset):
    def __init__(self, X, y):
        self.X = torch.tensor(X, dtype=torch.float32)
        self.y = torch.tensor(y, dtype=torch.float32)

    def __len__(self):
        return len(self.X)

    def __getitem__(self, idx):
        return self.X[idx], self.y[idx]


# ── 노트북 cell 9 sliding window 그대로 ───────────────────────────────────
def make_sliding_windows(df, input_len=288, output_len=36):
    X_list = []
    y_list = []

    for pid, group in df.groupby("id"):
        group = group.sort_values("time")
        glucose = group["gl_scaled"].values

        if len(glucose) < input_len + output_len:
            continue

        for i in range(len(glucose) - input_len - output_len + 1):
            x = glucose[i : i + input_len]
            y = glucose[i + input_len : i + input_len + output_len]
            X_list.append(x)
            y_list.append(y)

    X = np.array(X_list)
    y = np.array(y_list)
    X = X.reshape(X.shape[0], input_len, 1)
    return X, y


# ── 노트북 cell 21 학습 1 epoch ──────────────────────────────────────────
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


# ── 노트북 cell 23 평가 ──────────────────────────────────────────────────
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
    parser.add_argument("--csv", required=True, help="weinstock.csv path")
    parser.add_argument("--n-users", type=int, default=20,
                        help="처음 N명만 사용 (노트북의 SMALL_DATA_TEST 대체)")
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--output-dir", default="artifacts")
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    # ── seed 고정 (노트북 cell 1) ────────────────────────────────────────
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"device: {device}")

    # ── 데이터 로드 + N명 추출 (노트북 cell 5) ──────────────────────────
    df = pd.read_csv(args.csv)
    selected_ids = df["id"].unique()[: args.n_users]
    df = df[df["id"].isin(selected_ids)].copy()
    print(f"selected IDs: {selected_ids}")
    print(f"df shape: {df.shape}")

    # ── 전처리 (노트북 cell 7) ─────────────────────────────────────────
    df["time"] = pd.to_datetime(df["time"])
    df = df[["id", "time", "gl"]].copy()
    df = df.dropna(subset=["id", "time", "gl"])
    df = df.sort_values(["id", "time"]).reset_index(drop=True)

    scaler = StandardScaler()
    df["gl_scaled"] = scaler.fit_transform(df[["gl"]])

    # ── sliding window (노트북 cell 9) ─────────────────────────────────
    X, y = make_sliding_windows(df, input_len=INPUT_LEN, output_len=OUTPUT_LEN)
    print(f"X shape: {X.shape}, y shape: {y.shape}")

    # ── train/val/test split (노트북 cell 11) ──────────────────────────
    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.3, random_state=args.seed, shuffle=True,
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.5, random_state=args.seed, shuffle=True,
    )

    # ── DataLoader (노트북 cell 13) ────────────────────────────────────
    train_loader = DataLoader(CGMDataset(X_train, y_train),
                              batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(CGMDataset(X_val, y_val),
                            batch_size=BATCH_SIZE, shuffle=False)
    test_loader = DataLoader(CGMDataset(X_test, y_test),
                             batch_size=BATCH_SIZE, shuffle=False)

    # ── 모델 (노트북 cell 21 그대로) ───────────────────────────────────
    model = Seq2SeqGRU(
        input_dim=1, hidden_dim=64, num_layers=2,
        output_len=OUTPUT_LEN, dropout=0.2,
    ).to(device)

    criterion = nn.MSELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=LR)

    # ── 학습 (노트북 cell 25) ──────────────────────────────────────────
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

    # ── 테스트 평가 (노트북 cell 27 + 29) ──────────────────────────────
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
    print(f"노트북(10명/EPOCH5) 결과: MAE 38.07, RMSE 56.64")

    # ── 산출물 저장 ────────────────────────────────────────────────────
    with open(output_dir / "scaler.pkl", "wb") as f:
        pickle.dump(scaler, f)

    metadata = {
        "input_len": INPUT_LEN,
        "output_len": OUTPUT_LEN,
        "hidden_dim": 64,
        "num_layers": 2,
        "dropout": 0.2,
        "horizon_minutes": OUTPUT_LEN * 5,
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
