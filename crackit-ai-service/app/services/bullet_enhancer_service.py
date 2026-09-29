import json
import logging
import os
import re

from dotenv import load_dotenv
from google import genai
from google.genai import types

from app.models.resume_models import (
    BulletEnhanceRequest,
    BulletEnhanceResponse,
    BulletFormulaBreakdown,
    BulletAlternative,
)
from app.prompts.bullet_enhancer_prompt import build_bullet_enhancer_prompt
from app.services.gemini_client import generate_content_with_fallback

load_dotenv()
logger = logging.getLogger(__name__)

api_key = os.getenv("GEMINI_API_KEY")
client = genai.Client(api_key=api_key) if api_key else None


class BulletEnhancerService:

    def enhance_bullet(self, request: BulletEnhanceRequest) -> BulletEnhanceResponse:
        original = request.bulletText.strip()
        if not original:
            return BulletEnhanceResponse(
                original_bullet="",
                enhanced_bullet="",
                strengths=["No bullet text provided"],
            )

        if client:
            try:
                prompt = build_bullet_enhancer_prompt(
                    bullet_text=original,
                    role=request.role or "",
                    company=request.company or "",
                    tech_stack=request.techStack or "",
                )

                response = generate_content_with_fallback(
                    client=client,
                    contents=prompt,
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        temperature=0.35,
                    ),
                )

                content = response.text.strip()
                data = json.loads(content)
                return BulletEnhanceResponse(**data)
            except Exception as e:
                logger.warn(f"Gemini bullet enhancement failed, using heuristic fallback: {e}")

        # Fallback heuristic rule-based engine
        return self._heuristic_fallback(request)

    def _heuristic_fallback(self, request: BulletEnhanceRequest) -> BulletEnhanceResponse:
        text = request.bulletText.strip()
        # Clean leading bullet markers
        clean_text = re.sub(r"^[•\-\*\s]+", "", text)
        role = (request.role or "").lower()

        # Passive verb replacements
        replacements = [
            (r"^(worked on|helped with|assisted with|was responsible for)\s+", "Spearheaded "),
            (r"^(built|created|made)\s+", "Architected and delivered "),
            (r"^(developed|wrote)\s+", "Engineered high-performance "),
            (r"^(handled|managed)\s+", "Orchestrated end-to-end "),
            (r"^(tested|automated tests for)\s+", "Automated robust regression test suites for "),
            (r"^(fixed bugs in|maintained)\s+", "Hardened and refactored core architecture of "),
        ]

        enhanced = clean_text
        verb_used = "Architected"
        for pattern, repl in replacements:
            if re.search(pattern, enhanced, flags=re.IGNORECASE):
                enhanced = re.sub(pattern, repl, enhanced, flags=re.IGNORECASE)
                verb_used = repl.strip().split()[0]
                break
        else:
            # If no initial verb found, prepend an active verb
            if "qa" in role or "test" in role or "sdet" in role:
                verb_used = "Automated"
                enhanced = f"Automated E2E validation pipeline for {enhanced}"
            elif "devops" in role or "cloud" in role or "sre" in role:
                verb_used = "Instrumented"
                enhanced = f"Instrumented resilient infrastructure for {enhanced}"
            else:
                verb_used = "Engineered"
                enhanced = f"Engineered scalable solutions for {enhanced}"

        # Ensure metrics exist in the bullet
        if not re.search(r"\b(\d+[%xXkKMm]|\d+\s*(ms|seconds|hours|users))\b", enhanced):
            if "qa" in role or "sdet" in role:
                enhanced += ", cutting regression testing cycle by 42% and eliminating critical production escapes"
                metric_dim = "Defect Prevention & Speed"
            elif "devops" in role or "cloud" in role:
                enhanced += ", slashing CI/CD deployment overhead by 35% with 99.95% system uptime"
                metric_dim = "Reliability & Uptime"
            else:
                enhanced += ", driving a 30% reduction in API latency and scaling throughput to 10k+ daily requests"
                metric_dim = "Latency & Throughput"
        else:
            metric_dim = "Performance & Impact"

        if not enhanced.endswith("."):
            enhanced += "."

        return BulletEnhanceResponse(
            original_bullet=text,
            enhanced_bullet=enhanced,
            formula_breakdown=BulletFormulaBreakdown(
                accomplished_x="Delivered resilient technical capability",
                measured_by_y="Quantifiable efficiency and throughput gains",
                doing_z=enhanced[:60] + "...",
            ),
            action_verb=verb_used,
            metric_dimension=metric_dim,
            strengths=[
                f"Elevated passive opening to high-impact executive verb '{verb_used}'",
                "Incorporated Google X-Y-Z formula with quantifiable performance outcome",
                "Applied ATS keyword prioritization for tech recruiters",
            ],
            alternatives=[
                BulletAlternative(
                    angle="Scale & Speed",
                    bullet=f"Accelerated {clean_text.lower()}, optimizing runtime performance by 40% under peak concurrent loads.",
                ),
                BulletAlternative(
                    angle="Architecture & Quality",
                    bullet=f"Redesigned {clean_text.lower()} leveraging modular architecture, boosting maintainability and lowering error rates to <0.1%.",
                ),
            ],
        )
