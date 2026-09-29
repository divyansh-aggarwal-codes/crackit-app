import json
import os
from dotenv import load_dotenv
from google import genai
from google.genai import types
from app.prompts.interview_chat_prompt import get_interview_chat_prompt
from app.services.gemini_client import generate_content_with_fallback

load_dotenv()

class InterviewChatService:

    def __init__(self):
        self.client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))

    def chat(self, data: dict) -> dict:
        system_prompt = get_interview_chat_prompt(data)
        message = data.get("message", "")
        history = data.get("history", [])

        # build contents from history
        contents = []
        for msg in history:
            role = "user" if msg.get("role") == "user" else "model"
            contents.append({"role": role, "parts": [{"text": msg.get("content", "")}]})

        # add current message
        contents.append({"role": "user", "parts": [{"text": message}]})

        response = generate_content_with_fallback(
            client=self.client,
            contents=contents,
            config=types.GenerateContentConfig(
                system_instruction=system_prompt,
                temperature=0.8,
                max_output_tokens=1024,
            )
        )

        return {"reply": response.text.strip()}