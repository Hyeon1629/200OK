import json
import logging
import os
import re
from typing import Any

from dotenv import load_dotenv
from google import genai

logger = logging.getLogger(__name__)

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
    candidates = response.candidates or []
    if candidates:
        finish_reason = getattr(candidates[0], "finish_reason", None)
        if finish_reason is not None and "MAX_TOKENS" in str(finish_reason).upper():
            logger.warning(
                "Gemini 응답이 잘렸습니다 (finish_reason=MAX_TOKENS, max_output_tokens=%d). "
                "max_output_tokens 증가를 검토하세요.",
                max_output_tokens,
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


def _json_dumps(value: Any) -> str:
    """프롬프트에 넣을 데이터를 한글이 깨지지 않는 JSON 문자열로 변환한다."""
    return json.dumps(value, ensure_ascii=False, indent=2, default=str)


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

Length rules (IMPORTANT — keep it short to avoid truncation):
- Write a CONCISE report. Each section: 2-3 short bullet points max, one sentence per bullet.
- Use bullet points only — no paragraphs, no repetition, no filler.
- Do not restate raw data; give only the key takeaway/insight.
- Whole report under ~800 Korean characters.

Section rules (IMPORTANT):
- A record section shows "- none" when there is no data for that type.
- Generate the analysis section ONLY for data types that have actual records.
- If a data type shows "- none", OMIT its section entirely (do not output its header or any line for it).
- Base "## Summary" and "## Recommended Actions" only on the data types that have records.

Use these sections (include a "## Xxx Analysis" section only when that data exists):
## Summary
- 2 or 3 key changes (based only on available data)
## Diet Analysis        ← include only if Diet records exist
- Analyze carbohydrates, sugar, calories, protein, sodium, and meal timing
## Sleep Analysis       ← include only if Sleep records exist
- Analyze sleep duration and quality
## Exercise Analysis    ← include only if Exercise records exist
- Analyze exercise volume and recovery
## Recommended Actions
- 3 concrete actions the user can try today (based only on available data)

User: {user_name} / {user_email}
Analysis period: {data.get("from") or "-"} ~ {data.get("to") or "-"}

[Diet records]
{_format_records(data.get("diets") or [])}

[Sleep records]
{_format_records(data.get("sleeps") or [])}

[Exercise records]
{_format_records(data.get("exercises") or [])}
"""


def _build_pain_prompt(payload: dict[str, Any]) -> str:
    """통증 분석 프롬프트를 생성한다."""
    pain = payload.get("pain") or {}
    optional_sections: list[str] = []

    section_map = [
        ("pain_history", "최근 1주일 같은 부위 통증 기록"),
        ("diets", "전날~당일 식단 기록"),
        ("exercises", "전날~당일 운동 기록"),
        ("sleeps", "전날~당일 수면 기록"),
        ("glucose", "전날~당일 혈당 기록"),
    ]
    for key, title in section_map:
        records = payload.get(key) or []
        if records:
            optional_sections.append(f"[{title}]\n{_format_records(records)}")

    extra_data = "\n\n".join(optional_sections) or "추가 생활 데이터 없음"

    return f"""
너는 CheckDang 앱의 통증 분석 AI야.
아래 payload만 근거로 이번 통증의 가능한 원인과 집에서 할 수 있는 간단 조치를 한국어로 작성해.

분석 원칙:
- 의학적 확정 진단처럼 말하지 말고, 가능한 요인 중심으로 설명한다.
- 데이터가 없는 항목은 추정하거나 언급하지 않는다.
- 최근 1주일 같은 부위 통증 기록이 있으면 재발 여부와 강도 추세(악화/완화/유지)를 함께 반영한다.
- 식단, 운동, 수면, 혈당 기록이 있으면 통증과 관련 있을 수 있는 생활 패턴만 선별한다.
- 응급 신호(심한 흉통, 마비, 호흡곤란, 고열, 외상 후 극심한 통증 등)가 의심되면 전문 진료 권고를 포함한다.
- 답변은 짧고 바로 앱에 저장할 수 있게 쓴다.

출력 규칙:
- 반드시 JSON 객체 하나만 출력한다.
- 코드펜스와 Markdown을 쓰지 않는다.
- 키는 정확히 "ai_cause", "ai_first_aid" 두 개만 사용한다.
- 각 값은 문자열이며, 각각 2~4문장 이내로 작성한다.

출력 예:
{{
  "ai_cause": "가능한 원인 설명",
  "ai_first_aid": "집에서 할 수 있는 간단 조치"
}}

[기본 정보]
user_id={payload.get("user_id") or "-"}
date={payload.get("date") or "-"}

[이번 통증]
{_json_dumps(pain)}

{extra_data}
"""


def _extract_json_object(text: str) -> dict[str, Any]:
    """Gemini 응답에서 JSON 객체를 추출해 파싱한다."""
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s*```$", "", cleaned)

    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", cleaned, flags=re.DOTALL)
        if not match:
            raise
        parsed = json.loads(match.group(0))

    if not isinstance(parsed, dict):
        raise ValueError("Gemini pain analysis response must be a JSON object.")
    return parsed


def _parse_pain_result(text: str) -> dict[str, str]:
    """Gemini 통증 분석 응답을 백엔드 계약에 맞게 정규화한다."""
    parsed = _extract_json_object(text)
    ai_cause = str(parsed.get("ai_cause") or "").strip()
    ai_first_aid = str(parsed.get("ai_first_aid") or "").strip()

    if not ai_cause or not ai_first_aid:
        raise ValueError("Gemini pain analysis response is missing required fields.")

    return {
        "ai_cause": ai_cause,
        "ai_first_aid": ai_first_aid,
    }


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
    return _generate_text(prompt, max_output_tokens=1500)


def analyze_health_report(data: dict[str, Any]) -> str:
    """식단·수면·운동 데이터를 종합해 Markdown 헬스 리포트를 반환한다."""
    prompt = _build_health_report_prompt(data)
    return _generate_text(prompt, max_output_tokens=4000)


def analyze_pain(payload: dict[str, Any]) -> dict[str, str]:
    """통증 + 생활데이터를 받아 원인분석/조치를 반환한다.

    gemini-2.5-flash는 thinking 모델이라 max_output_tokens가 thinking+출력 합산 예산으로 동작한다.
    payload가 크면 thinking 토큰(실측 ~1.2k~1.7k)이 예산을 잠식해 출력 JSON이 잘려(finish_reason=
    MAX_TOKENS) 파싱이 실패하므로, thinking이 동적으로 늘어도 출력이 잘리지 않게 8000으로 둔다.
    """
    prompt = _build_pain_prompt(payload)
    text = _generate_text(prompt, max_output_tokens=8000)
    return _parse_pain_result(text)
