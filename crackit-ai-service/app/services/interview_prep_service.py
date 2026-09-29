import json
import os
from dotenv import load_dotenv
from google import genai
from google.genai import types
from app.prompts.interview_prep_prompt import get_interview_prep_prompt
from app.services.gemini_client import generate_content_with_fallback

load_dotenv()

class InterviewPrepService:

    def __init__(self):
        self.client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

    def generate_prep(self, data: dict) -> dict:
        prompt = get_interview_prep_prompt(data)
        response = generate_content_with_fallback(
            client=self.client,
            contents=prompt,
            config=types.GenerateContentConfig(
                temperature=0.7,
                max_output_tokens=16384,
            )
        )
        raw = response.text.strip()
        # strip markdown if gemini wraps it
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        raw = raw.strip()
        return json.loads(raw)