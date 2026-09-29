import json
import os
from dotenv import load_dotenv
from google import genai
from google.genai import types
from app.prompts.roadmap_prompt import get_roadmap_prompt
from app.services.gemini_client import generate_content_with_fallback

load_dotenv()

class RoadmapService:

    def __init__(self):
        self.client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

    def generate_roadmap(self, data: dict) -> dict:
        prompt = get_roadmap_prompt(data)
        response = generate_content_with_fallback(
            client=self.client,
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                temperature=0.35,
                max_output_tokens=16384,
            )
        )
        raw = response.text.strip()
        if raw.startswith("```"):
            raw = raw.split("```")[1]
            if raw.startswith("json"):
                raw = raw[4:]
        raw = raw.strip()
        return json.loads(raw)
