import json
import os

from dotenv import load_dotenv
from google import genai
from google.genai import types

from app.models.resume_models import ResumeTailoringRequest, ResumeTailoringResponse
from app.prompts.resume_tailoring_prompt import build_resume_tailoring_prompt
from app.services.gemini_client import generate_content_with_fallback

load_dotenv()

client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))


class ResumeTailoringService:

    def tailor_resume(self, request: ResumeTailoringRequest) -> ResumeTailoringResponse:
        prompt = build_resume_tailoring_prompt(
            jd_analysis=request.jdAnalysis,
            summary=request.summary,
            skills=request.skills,
            experiences=request.experiences,
            projects=request.projects,
            mode=request.mode or "strict"
        )

        response = generate_content_with_fallback(
            client=client,
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=ResumeTailoringResponse,
                temperature=0.35
            )
        )

        content = response.text.strip()

        try:
            data = json.loads(content)
        except json.JSONDecodeError:
            raise RuntimeError(f"Gemini returned invalid JSON: {content}")

        return ResumeTailoringResponse(**data)