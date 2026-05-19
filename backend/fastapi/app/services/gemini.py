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
