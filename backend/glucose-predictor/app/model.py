"""Seq2Seq GRU 혈당 예측 모델.

팀원의 glucose_model_1.ipynb cell 15/17/19 그대로 추출.
학습(train.py)·추론(main.py) 양쪽에서 import해 재사용한다.

입력:  (B, 288, 1)  — 24시간 5분 간격 정규화된 혈당
출력:  (B, 36)      — 3시간 5분 간격 예측 (정규화 스케일)
"""

from __future__ import annotations

import random

import torch
import torch.nn as nn


class EncoderGRU(nn.Module):
    def __init__(self, input_dim: int = 1, hidden_dim: int = 64,
                 num_layers: int = 2, dropout: float = 0.2):
        super().__init__()
        self.gru = nn.GRU(
            input_size=input_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0,
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        _, hidden = self.gru(x)
        return hidden


class DecoderGRU(nn.Module):
    def __init__(self, input_dim: int = 1, hidden_dim: int = 64,
                 num_layers: int = 2, dropout: float = 0.2):
        super().__init__()
        self.gru = nn.GRU(
            input_size=input_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0,
        )
        self.fc = nn.Linear(hidden_dim, 1)

    def forward(self, x: torch.Tensor, hidden: torch.Tensor):
        output, hidden = self.gru(x, hidden)
        pred = self.fc(output)
        return pred, hidden


class Seq2SeqGRU(nn.Module):
    def __init__(self, input_dim: int = 1, hidden_dim: int = 64,
                 num_layers: int = 2, output_len: int = 36,
                 dropout: float = 0.2):
        super().__init__()
        self.output_len = output_len
        self.encoder = EncoderGRU(input_dim, hidden_dim, num_layers, dropout)
        self.decoder = DecoderGRU(input_dim, hidden_dim, num_layers, dropout)

    def forward(self, src: torch.Tensor, target: torch.Tensor | None = None,
                teacher_forcing_ratio: float = 0.5) -> torch.Tensor:
        batch_size = src.size(0)
        hidden = self.encoder(src)
        decoder_input = src[:, -1:, :]   # 디코더 첫 입력 = 인코더 마지막 timestep

        outputs = []
        for t in range(self.output_len):
            pred, hidden = self.decoder(decoder_input, hidden)
            outputs.append(pred.squeeze(1))

            use_teacher_forcing = (
                target is not None and random.random() < teacher_forcing_ratio
            )
            if use_teacher_forcing:
                decoder_input = target[:, t].view(batch_size, 1, 1)
            else:
                decoder_input = pred

        return torch.cat(outputs, dim=1)
