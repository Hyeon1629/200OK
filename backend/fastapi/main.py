import os
from typing import Any

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from google import genai
from pydantic import BaseModel

load_dotenv()

app = FastAPI()

gemini_api_key = os.getenv("GEMINI_API_KEY")
if not gemini_api_key:
    raise RuntimeError("GEMINI_API_KEY is not set.")

client = genai.Client(api_key=gemini_api_key)


class DietAnalyzeRequest(BaseModel):
    diets: list[dict[str, Any]]


class AnalyzeResponse(BaseModel):
    answer: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/analyze/diet", response_model=AnalyzeResponse)
def analyze_diet(request: DietAnalyzeRequest) -> AnalyzeResponse:
    if not request.diets:
        return AnalyzeResponse(answer="분석할 식단 데이터가 아직 없어요.")

    prompt = f"""
너는 건강관리 앱의 식단 분석 AI야.
아래 사용자의 식단 데이터를 보고 한국어로 짧고 친절하게 분석해줘.

조건:
- 의학적 진단처럼 말하지 말 것
- 개선점은 3개 이내로 말할 것
- 사용자가 바로 실천할 수 있게 말할 것
- 답변은 5문장 이내로 작성할 것

식단 데이터:
{request.diets}
"""

    try:
        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=prompt,
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Gemini API request failed.") from exc

    if not response.text:
        raise HTTPException(status_code=502, detail="Gemini API response is empty.")

    return AnalyzeResponse(answer=response.text)
