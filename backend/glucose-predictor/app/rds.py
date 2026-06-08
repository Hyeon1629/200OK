"""RDS(MySQL) 읽기 — 혈당예측 7피처용 carbs(diet) / bolus(insulin_record) 소싱.

아키텍처 B: predictor 가 RDS 를 직접 조회한다.
- carbs : RDS `diet`.carbohydrate (삼성헬스 Nutrition 동기화분)
- bolus : RDS `insulin_record` 중 insulinType=RAPID(속효성) 의 dosage

⚠️ 모델 미연결 단계의 사전 구축. 자격은 ENV(RDS_URL/RDS_USERNAME/RDS_PASSWORD,
   checkdang/springboot 시크릿 주입)로 받는다. 호출 전까지 dormant.
"""

from __future__ import annotations

import logging
import os
from datetime import datetime
from urllib.parse import urlparse

logger = logging.getLogger(__name__)

# bolus 로 간주할 insulin_type (RAPID enum + 한글 라벨)
BOLUS_TYPES = {"RAPID", "속효성"}


def _conn_params() -> dict:
    """Spring JDBC URL(jdbc:mysql://host:port/db?...) 을 pymysql 파라미터로 파싱."""
    raw = os.getenv("RDS_URL", "")
    cleaned = raw[len("jdbc:"):] if raw.startswith("jdbc:") else raw
    u = urlparse(cleaned)  # mysql://host:port/db
    return {
        "host": u.hostname,
        "port": u.port or 3306,
        "user": os.getenv("RDS_USERNAME"),
        "password": os.getenv("RDS_PASSWORD"),
        "database": (u.path or "/checkdang").lstrip("/") or "checkdang",
        "connect_timeout": 5,
        "charset": "utf8mb4",
    }


def _query(sql: str, args: tuple):
    import pymysql  # lazy import — 미연결 단계에서 import 비용 회피

    conn = pymysql.connect(**_conn_params())
    try:
        with conn.cursor() as cur:
            cur.execute(sql, args)
            return cur.fetchall()
    finally:
        conn.close()


def get_carbs_events(user_id: str, frm: datetime, to: datetime) -> list[tuple[datetime, float]]:
    """기간 내 탄수화물 섭취 이벤트 [(recorded_at, carbohydrate_g), ...] (NULL/0 제외)."""
    rows = _query(
        "SELECT recorded_at, carbohydrate FROM diet "
        "WHERE user_id=%s AND recorded_at BETWEEN %s AND %s "
        "AND carbohydrate IS NOT NULL AND carbohydrate > 0 "
        "ORDER BY recorded_at ASC",
        (user_id, frm, to),
    )
    return [(r[0], float(r[1])) for r in rows]


def get_bolus_events(user_id: str, frm: datetime, to: datetime) -> list[tuple[datetime, float]]:
    """기간 내 bolus(속효성 인슐린) 이벤트 [(injected_at, dosage_units), ...]."""
    rows = _query(
        "SELECT injected_at, dosage, insulin_type FROM insulin_record "
        "WHERE user_id=%s AND injected_at BETWEEN %s AND %s "
        "ORDER BY injected_at ASC",
        (user_id, frm, to),
    )
    out: list[tuple[datetime, float]] = []
    for injected_at, dosage, insulin_type in rows:
        if insulin_type and str(insulin_type).strip().upper() in {t.upper() for t in BOLUS_TYPES}:
            try:
                out.append((injected_at, float(dosage)))
            except (TypeError, ValueError):
                continue
    return out
