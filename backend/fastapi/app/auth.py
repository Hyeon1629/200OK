"""AWS Cognito JWT(RS256) 검증.

FastAPI 라우터에 `Depends(verify_token)`로 부착해 보호한다.
운영 풀(mobile-2, ap-northeast-2_mb9vuwfui)이 발급한 토큰을 JWKS 공개키로 검증한다.

환경변수:
- COGNITO_ISSUER_URI   (필수) 예: https://cognito-idp.ap-northeast-2.amazonaws.com/ap-northeast-2_mb9vuwfui
- COGNITO_APP_CLIENT_IDS (선택, 콤마구분) 설정 시 aud(id token)/client_id(access token) 화이트리스트 검증
"""

from __future__ import annotations

import logging
import os
from typing import Optional

import jwt
from fastapi import Header, HTTPException
from jwt import PyJWKClient

logger = logging.getLogger(__name__)

COGNITO_ISSUER_URI = os.getenv("COGNITO_ISSUER_URI", "").rstrip("/")
_client_ids_raw = os.getenv("COGNITO_APP_CLIENT_IDS", "")
ALLOWED_CLIENT_IDS = [c.strip() for c in _client_ids_raw.split(",") if c.strip()]

# JWKS 클라이언트 (lazy 초기화 + PyJWKClient 자체 캐시)
_jwk_client: Optional[PyJWKClient] = None


def _get_jwk_client() -> PyJWKClient:
    global _jwk_client
    if not COGNITO_ISSUER_URI:
        logger.error("COGNITO_ISSUER_URI 미설정 — JWT 검증 불가")
        raise HTTPException(status_code=500, detail="서버 인증 설정 오류입니다.")
    if _jwk_client is None:
        jwks_url = f"{COGNITO_ISSUER_URI}/.well-known/jwks.json"
        _jwk_client = PyJWKClient(jwks_url)
    return _jwk_client


def verify_token(authorization: str = Header(None)) -> dict:
    """Authorization: Bearer <token> 검증 → {sub, email, token_use} 반환."""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="인증 토큰이 필요합니다.")

    token = authorization.split(" ", 1)[1].strip()

    try:
        signing_key = _get_jwk_client().get_signing_key_from_jwt(token).key
        claims = jwt.decode(
            token,
            signing_key,
            algorithms=["RS256"],
            issuer=COGNITO_ISSUER_URI,
            options={"verify_aud": False},  # Cognito access token엔 aud 없음 → 아래서 수동 검증
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="만료된 토큰입니다.")
    except jwt.InvalidTokenError as exc:
        logger.warning("JWT 검증 실패: %s", exc)
        raise HTTPException(status_code=401, detail="유효하지 않은 토큰입니다.")
    except HTTPException:
        raise
    except Exception as exc:  # JWKS fetch 실패 등
        logger.error("JWT 검증 중 오류: %s", exc)
        raise HTTPException(status_code=401, detail="토큰을 검증할 수 없습니다.")

    # token_use 확인 (id 또는 access)
    token_use = claims.get("token_use")
    if token_use not in ("id", "access"):
        raise HTTPException(status_code=401, detail="지원하지 않는 토큰 종류입니다.")

    # app client 화이트리스트 (설정된 경우에만)
    if ALLOWED_CLIENT_IDS:
        # id token → aud, access token → client_id
        client_id = claims.get("aud") or claims.get("client_id")
        if client_id not in ALLOWED_CLIENT_IDS:
            raise HTTPException(status_code=401, detail="허용되지 않은 클라이언트입니다.")

    return {
        "sub": claims.get("sub"),
        "email": claims.get("email"),
        "token_use": token_use,
    }
