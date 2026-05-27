import os
from typing import Any

from dotenv import load_dotenv
from google import genai

load_dotenv()

_api_key = os.getenv("GEMINI_API_KEY")
_model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

_client = None


def _get_client():
    """Lazy 초기화 — GEMINI_API_KEY 없으면 호출 시점에 에러 발생"""
    global _client
    if _client is None:
        if not _api_key:
            raise RuntimeError("GEMINI_API_KEY is not set.")
        _client = genai.Client(api_key=_api_key)
    return _client


def _generate_text(prompt: str, max_output_tokens: int) -> str:
    """Gemini 호출 공통 헬퍼.

    thinking_config는 명시하지 않으므로 모델 기본값(2.5 시리즈는 dynamic)을 사용한다.
    """
    client = _get_client()
    response = client.models.generate_content(
        model=_model,
        contents=prompt,
        config={
            "temperature": 0.4,
            "max_output_tokens": max_output_tokens,
        },
    )
    return response.text or ""


def _format_records(records: list[dict[str, Any]]) -> str:
    """레코드 리스트를 'key=value' 라인 묶음으로 직렬화한다."""
    if not records:
        return "- none"

    lines: list[str] = []
    for record in records:
        values = [f"{key}={value}" for key, value in record.items()]
        lines.append("- " + ", ".join(values))
    return "\n".join(lines)


def _build_health_report_prompt(data: dict[str, Any]) -> str:
    """종합 헬스 리포트 프롬프트를 생성한다."""
    user = data.get("user") or {}
    user_name = user.get("name") or "-"
    user_email = user.get("email") or "-"

    return f"""
You are the health data analysis assistant for CheckDang, a diabetes and lifestyle management app.
Write the report in Korean Markdown for direct frontend rendering.
Use only the database values below as evidence.
Do not provide a definitive medical diagnosis. If there are warning signs, recommend consulting a medical professional.

Follow this exact structure:
## Summary
- 2 or 3 key changes
## Diet Analysis
- Analyze carbohydrates, sugar, calories, protein, sodium, and meal timing
## Sleep Analysis
- Analyze sleep duration and quality
## Exercise Analysis
- Analyze exercise volume and recovery
## Recommended Actions
- 3 concrete actions the user can try today

User: {user_name} / {user_email}
Analysis period: {data.get("from") or "-"} ~ {data.get("to") or "-"}

[Diet records]
{_format_records(data.get("diets") or [])}

[Sleep records]
{_format_records(data.get("sleeps") or [])}

[Exercise records]
{_format_records(data.get("exercises") or [])}
"""


def analyze_diet(diets: list[dict[str, Any]]) -> str:
    """식단 데이터를 받아 Gemini로 한국어 분석 결과를 반환한다."""
    prompt = f"""
너는 건강관리 앱의 식단 분석 AI야.
아래 사용자의 식단 데이터를 보고 한국어로 짧고 친절하게 분석해줘.

조건:
- 의학적 진단처럼 말하지 말 것
- 개선점은 3개 이내로 말할 것
- 사용자가 바로 실천할 수 있게 말할 것
- 답변은 5문장 이내로 작성할 것

식단 데이터:
{diets}
"""
    return _generate_text(prompt, max_output_tokens=600)


def analyze_health_report(data: dict[str, Any]) -> str:
    """식단·수면·운동 데이터를 종합해 Markdown 헬스 리포트를 반환한다."""
    prompt = _build_health_report_prompt(data)
    return _generate_text(prompt, max_output_tokens=4000)


def analyze_pain(pain_data: dict[str, Any]) -> tuple[str, str]:
    """통증 데이터를 받아 (ai_cause, ai_first_aid) 튜플을 반환한다."""
    body_part = pain_data.get("body_part", "-")
    pain_types = ", ".join(pain_data.get("pain_types") or [])
    intensity = pain_data.get("intensity", "-")

    prompt = f"""
너는 의료 보조 AI야. 아래 통증 기록을 보고 한국어로 분석해줘.

통증 부위: {body_part}
통증 종류: {pain_types}
통증 강도: {intensity} / 10

다음 두 가지를 반드시 아래 형식으로만 답해줘. 다른 텍스트는 절대 포함하지 마.

[원인]
(통증 원인을 2~3문장으로 설명. 의학적 진단이 아닌 가능성 설명)

[응급조치]
(지금 당장 할 수 있는 조치를 2~3문장으로 설명. 통증 강도 7 이상이면 의료진 상담 권장 포함)
"""

    raw = _generate_text(prompt, max_output_tokens=600)

    # [원인] / [응급조치] 파싱
    ai_cause, ai_first_aid = "", ""
    if "[원인]" in raw and "[응급조치]" in raw:
        cause_part = raw.split("[원인]")[1].split("[응급조치]")[0].strip()
        first_aid_part = raw.split("[응급조치]")[1].strip()
        ai_cause = cause_part
        ai_first_aid = first_aid_part
    else:
        # 파싱 실패 시 전체를 원인으로
        ai_cause = raw.strip()
        ai_first_aid = "통증이 지속되면 의료진과 상담하세요."

    return ai_cause, ai_first_aid
