import json
import logging
import os
import re
from typing import Any, Dict, List

import fitz  # PyMuPDF
from dotenv import load_dotenv
from google import genai
from google.genai import types

from app.models.resume_models import (
    ParsedBullet,
    ParsedEducation,
    ParsedExperience,
    ParsedProject,
    ParsedResume,
    ParsedSkill,
)
from app.prompts.resume_parse_prompt import build_resume_parse_prompt
from app.services.gemini_client import generate_content_with_fallback

load_dotenv()
logger = logging.getLogger(__name__)


class ResumeParseService:

    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        self.client = genai.Client(api_key=api_key) if api_key else None

    def parse_pdf(self, pdf_bytes: bytes) -> ParsedResume:
        # 1. Attempt text extraction via PyMuPDF
        text = ""
        try:
            doc = fitz.open(stream=pdf_bytes, filetype="pdf")
            for page in doc:
                text += page.get_text() + "\n"
            doc.close()
        except Exception as e:
            logger.warning(f"PyMuPDF text extraction failed: {e}")
            text = ""

        # 2. Try Gemini LLM extraction
        gemini_data = None
        if self.client:
            try:
                gemini_data = self._call_gemini(text, pdf_bytes)
            except Exception as e:
                logger.error(f"Gemini resume parsing failed: {e}", exc_info=True)

        if gemini_data:
            try:
                return self._sanitize_and_build(gemini_data)
            except Exception as e:
                logger.error(f"Failed to sanitize Gemini output: {e}", exc_info=True)

        # 3. Fallback: Heuristic extraction from raw text if Gemini unavailable/failed
        logger.info("Using local heuristic fallback parser for resume")
        return self._heuristic_fallback_parse(text)

    def _call_gemini(self, text: str, pdf_bytes: bytes) -> Dict[str, Any]:
        if text.strip() and len(text.strip()) > 30:
            prompt = build_resume_parse_prompt(text)
            contents = prompt
        else:
            pdf_part = types.Part.from_bytes(data=pdf_bytes, mime_type="application/pdf")
            prompt = build_resume_parse_prompt("Extract all resume details accurately from the attached PDF document.")
            contents = [pdf_part, prompt]

        response = generate_content_with_fallback(
            client=self.client,
            contents=contents,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                temperature=0.2,
                max_output_tokens=16384,
            ),
        )

        content = (response.text or "").strip()
        if not content:
            raise RuntimeError("Gemini returned empty response text")

        # Strip potential markdown fences
        if "```" in content:
            parts = content.split("```")
            for part in parts:
                cleaned = part.strip()
                if cleaned.startswith("json"):
                    cleaned = cleaned[4:].strip()
                if cleaned.startswith("{") and cleaned.endswith("}"):
                    content = cleaned
                    break
        content = content.strip()

        return json.loads(content)

    def _sanitize_and_build(self, data: Dict[str, Any]) -> ParsedResume:
        # Sanitize summary
        summary = str(data.get("summary") or "").strip()

        # Sanitize skills
        raw_skills = data.get("skills") or []
        skills: List[ParsedSkill] = []
        if isinstance(raw_skills, list):
            for s in raw_skills:
                if isinstance(s, dict):
                    name = str(s.get("skillName") or s.get("name") or "").strip()
                    if name:
                        years = s.get("yearsUsed")
                        try:
                            years_int = int(years) if years is not None else 0
                        except Exception:
                            years_int = 0
                        skills.append(
                            ParsedSkill(
                                skillName=name,
                                category=str(s.get("category") or "Other").strip(),
                                proficiencyLevel=str(s.get("proficiencyLevel") or "Advanced").strip(),
                                yearsUsed=years_int,
                            )
                        )
                elif isinstance(s, str) and s.strip():
                    skills.append(
                        ParsedSkill(
                            skillName=s.strip(),
                            category="Other",
                            proficiencyLevel="Advanced",
                            yearsUsed=0,
                        )
                    )

        # Sanitize experiences
        raw_exps = data.get("experiences") or []
        experiences: List[ParsedExperience] = []
        if isinstance(raw_exps, list):
            for e in raw_exps:
                if not isinstance(e, dict):
                    continue
                company = str(e.get("companyName") or e.get("company") or "").strip()
                role = str(e.get("role") or e.get("title") or "").strip()
                desc = str(e.get("description") or "").strip()

                raw_bullets = e.get("bullets") or []
                bullets: List[ParsedBullet] = []
                if isinstance(raw_bullets, list):
                    for b in raw_bullets:
                        if isinstance(b, dict):
                            b_text = str(b.get("bulletText") or b.get("text") or "").strip()
                            b_tech = str(b.get("technologies") or "").strip()
                            if b_text:
                                bullets.append(ParsedBullet(bulletText=b_text, technologies=b_tech))
                        elif isinstance(b, str) and b.strip():
                            bullets.append(ParsedBullet(bulletText=b.strip(), technologies=""))

                experiences.append(
                    ParsedExperience(
                        companyName=company,
                        role=role,
                        startDate=str(e.get("startDate")) if e.get("startDate") else None,
                        endDate=str(e.get("endDate")) if e.get("endDate") else None,
                        currentCompany=bool(e.get("currentCompany")),
                        description=desc,
                        bullets=bullets,
                    )
                )

        # Sanitize projects
        raw_projs = data.get("projects") or []
        projects: List[ParsedProject] = []
        if isinstance(raw_projs, list):
            for p in raw_projs:
                if not isinstance(p, dict):
                    continue
                title = str(p.get("title") or p.get("name") or "").strip()
                desc = str(p.get("description") or "").strip()
                tech = str(p.get("techStack") or "").strip()
                github = str(p.get("githubUrl") or "").strip()
                impact = str(p.get("impactMetrics") or "").strip()

                raw_bullets = p.get("bullets") or []
                bullets: List[ParsedBullet] = []
                if isinstance(raw_bullets, list):
                    for b in raw_bullets:
                        if isinstance(b, dict):
                            b_text = str(b.get("bulletText") or b.get("text") or "").strip()
                            b_tech = str(b.get("technologies") or "").strip()
                            if b_text:
                                bullets.append(ParsedBullet(bulletText=b_text, technologies=b_tech))
                        elif isinstance(b, str) and b.strip():
                            bullets.append(ParsedBullet(bulletText=b.strip(), technologies=""))

                # If description is empty but bullets exist, assemble description from bullets
                if not desc and bullets:
                    desc = "\n".join(b.bulletText for b in bullets)

                projects.append(
                    ParsedProject(
                        title=title,
                        description=desc,
                        techStack=tech,
                        githubUrl=github,
                        impactMetrics=impact,
                        bullets=bullets,
                    )
                )

        # Sanitize education
        raw_edu = data.get("education") or []
        education: List[ParsedEducation] = []
        if isinstance(raw_edu, dict):
            raw_edu = [raw_edu]
        elif isinstance(raw_edu, str) and raw_edu.strip():
            education.append(ParsedEducation(degree=raw_edu.strip()))
            raw_edu = []

        if isinstance(raw_edu, list):
            for ed in raw_edu:
                if isinstance(ed, dict):
                    education.append(
                        ParsedEducation(
                            degree=str(ed.get("degree") or "").strip(),
                            institution=str(ed.get("institution") or ed.get("college") or ed.get("university") or "").strip(),
                            year=str(ed.get("year") or ed.get("graduationYear") or "").strip(),
                            score=str(ed.get("score") or ed.get("cgpa") or ed.get("grade") or "").strip(),
                        )
                    )
                elif isinstance(ed, str) and ed.strip():
                    education.append(ParsedEducation(degree=ed.strip()))

        return ParsedResume(
            summary=summary,
            skills=skills,
            experiences=experiences,
            projects=projects,
            education=education,
        )

    def _heuristic_fallback_parse(self, text: str) -> ParsedResume:
        lines = [line.strip() for line in text.split("\n") if line.strip()]
        summary = ""
        skills: List[ParsedSkill] = []
        experiences: List[ParsedExperience] = []
        projects: List[ParsedProject] = []
        education: List[ParsedEducation] = []

        # Common tech keywords to extract
        known_techs = [
            ("Java", "Languages"), ("Python", "Languages"), ("JavaScript", "Languages"),
            ("TypeScript", "Languages"), ("SQL", "Languages"), ("C++", "Languages"), ("Go", "Languages"),
            ("Spring Boot", "Backend"), ("Node.js", "Backend"), ("Express", "Backend"),
            ("React", "Frontend"), ("JPA/Hibernate", "Backend"), ("REST APIs", "Backend"),
            ("MySQL", "Databases"), ("PostgreSQL", "Databases"), ("MongoDB", "Databases"),
            ("Redis", "Caching & Reliability"), ("Apache Kafka", "Messaging & Distributed Systems"),
            ("AWS", "Cloud"), ("Docker", "DevOps"), ("Kubernetes", "DevOps"), ("Git", "Tools"),
            ("JUnit", "Tools"), ("Mockito", "Tools"), ("Linux", "Tools"), ("Maven", "Tools"),
        ]

        lower_text = text.lower()
        for tech, cat in known_techs:
            if re.search(r"\b" + re.escape(tech.lower()) + r"\b", lower_text):
                skills.append(ParsedSkill(skillName=tech, category=cat, proficiencyLevel="Advanced", yearsUsed=3))

        # Look for summary section
        sum_match = re.search(r"(?:PROFESSIONAL SUMMARY|SUMMARY|PROFILE|OBJECTIVE)\s*\n+([^A-Z\n]{10,}[^\n]+(?:\n+[^\n]+){1,5})", text, re.IGNORECASE)
        if sum_match:
            summary = sum_match.group(1).replace("\n", " ").strip()
        elif lines:
            # First non-header line of text
            for line in lines[1:5]:
                if len(line) > 60:
                    summary = line
                    break

        return ParsedResume(
            summary=summary,
            skills=skills,
            experiences=experiences,
            projects=projects,
            education=education,
        )