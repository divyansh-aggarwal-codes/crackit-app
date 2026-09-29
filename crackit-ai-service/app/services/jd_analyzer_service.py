import json
import os

from dotenv import load_dotenv
from google import genai
from google.genai import types

from app.models.jd_models import JDAnalysisResponse
from app.models.jd_models import JDAnalysisRequest, JDAnalysisResponse
from app.prompts.jd_analysis_prompt import build_jd_analysis_prompt

load_dotenv()

client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))


class JDAnalyzerService:

    def analyze_jd(self, request: JDAnalysisRequest) -> JDAnalysisResponse:
        prompt = build_jd_analysis_prompt(
            jd_text=request.jdText,
            summary=request.summary,
            skills=request.skills,
            experiences=request.experiences,
            projects=request.projects
        )
        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=JDAnalysisResponse,
                temperature=0.3
            )
        )

        content = response.text.strip()

        data = json.loads(content)
        resp = JDAnalysisResponse(**data)

        # Deterministic verification of ATS keywords against candidate resume
        resume_corpus = []
        if request.summary:
            resume_corpus.append(request.summary.lower())
        for s in request.skills:
            if hasattr(s, "skillName") and s.skillName:
                resume_corpus.append(s.skillName.lower())
        for e in request.experiences:
            if hasattr(e, "companyName") and e.companyName:
                resume_corpus.append(e.companyName.lower())
            if hasattr(e, "role") and e.role:
                resume_corpus.append(e.role.lower())
            if hasattr(e, "description") and e.description:
                resume_corpus.append(e.description.lower())
            for b in (getattr(e, "bullets", []) or []):
                bt = getattr(b, "bulletText", "") if hasattr(b, "bulletText") else str(b)
                if bt:
                    resume_corpus.append(bt.lower())
        for p in request.projects:
            if hasattr(p, "title") and p.title:
                resume_corpus.append(p.title.lower())
            if hasattr(p, "techStack") and p.techStack:
                resume_corpus.append(p.techStack.lower())
            if hasattr(p, "description") and p.description:
                resume_corpus.append(p.description.lower())
            if hasattr(p, "impactMetrics") and p.impactMetrics:
                resume_corpus.append(p.impactMetrics.lower())

        full_resume_text = " ".join(resume_corpus)

        matched = list(resp.matchedKeywords) if resp.matchedKeywords else []
        missing = list(resp.missingKeywords) if resp.missingKeywords else []

        all_keywords = list(dict.fromkeys(resp.atsKeywords + (resp.requiredSkills[:8] if resp.requiredSkills else [])))
        if full_resume_text.strip():
            matched_set = set(m.lower() for m in matched)
            missing_set = set(m.lower() for m in missing)
            final_matched = []
            final_missing = []

            for kw in all_keywords:
                kw_clean = kw.strip()
                if not kw_clean:
                    continue
                kw_lower = kw_clean.lower()
                # Check if keyword exists in resume corpus
                if kw_lower in full_resume_text or kw_lower in matched_set:
                    if kw_clean not in final_matched:
                        final_matched.append(kw_clean)
                else:
                    if kw_clean not in final_missing and kw_clean not in final_matched:
                        final_missing.append(kw_clean)

            resp.matchedKeywords = final_matched
            resp.missingKeywords = final_missing
        else:
            resp.matchedKeywords = []
            resp.missingKeywords = all_keywords

        return resp