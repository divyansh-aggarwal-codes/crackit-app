import json


def build_jd_analysis_prompt(
        jd_text: str,
        summary: str,
        skills: list,
        experiences: list,
        projects: list
) -> str:

    has_resume = any([summary, skills, experiences, projects])

    resume_section = ""
    if has_resume:
        resume_section = f"""
Candidate Resume:

Summary:
{summary if summary else "Not provided"}

Skills:
{json.dumps([s.dict() if hasattr(s, 'dict') else s for s in skills], indent=2)}

Experiences:
{json.dumps([e.dict() if hasattr(e, 'dict') else e for e in experiences], indent=2)}

Projects:
{json.dumps([p.dict() if hasattr(p, 'dict') else p for p in projects], indent=2)}
"""

    return f"""
You are a Staff Technical Recruiter and Principal Software Engineering Hiring Manager.

Perform a rigorous, production-grade architectural analysis of the Job Description and evaluate the candidate's alignment.

{resume_section}

---
### ANALYSIS INSTRUCTIONS:

1. **requiredSkills**:
   Extract core, mandatory technical skills (languages, frameworks, distributed systems, databases). Omit non-technical generic buzzwords like "communication" or "team player".

2. **preferredSkills**:
   Extract secondary or "nice-to-have" skills (cloud tools, DevOps, monitoring, specific niche libraries).

3. **experienceLevel**:
   Normalized seniority level: "Entry", "Junior", "Mid-Level", "Senior", "Lead", or "Principal/Staff".

4. **importantTopics**:
   Identify 6 to 10 deep, role-specific technical and architectural topics the candidate will be grilled on in technical rounds for this role.
   Do NOT output trivial generic words like "Java" or "Testing".
   Calibrate to the role's actual domain:
   - For Backend: "Distributed In-Memory Caching & Cache-Aside Invalidation", "Event-Driven Microservices & Kafka Partition Strategy", "Relational Query Execution Plans & Index Optimization", "Concurrency & Thread Safety".
   - For QA/SDET: "E2E Test Architecture (Page Object Model)", "Parallel Test Execution & CI/CD Pipeline Gates", "Flaky Test Detection & Quarantining", "API Mocking, Contract Testing & Service Virtualization", "Performance/Load Testing & Bottleneck Identification".
   - For Frontend/Mobile: "Core Web Vitals & Render Pipeline Performance", "Client-Side Caching & State Management", "Micro-Frontends & Module Federation", "Network Payload & Bundle Splitting".
   - For DevOps/SRE: "Zero-Downtime Deployment Strategies (Canary/Blue-Green)", "Infrastructure-as-Code State Management", "Observability (Distributed Tracing, Prometheus, SLIs/SLOs)", "Kubernetes Pod Auto-scaling & Resource Quotas".

5. **summary**:
   Provide a high-signal, 3-part executive analysis (formatted cleanly with markdown bolding):
   - **Role Scope & Bar**: Brief synthesis of the role's mission, team expectations, and tech stack scale.
   - **Candidate Gap Analysis**: Explicitly call out:
     * *Direct Strengths*: Where candidate matches 100%.
     * *Transferable Skills*: Where candidate has adjacent experience (e.g. RabbitMQ -> Kafka, Postgres -> MySQL).
     * *Critical Gaps (Dealbreakers)*: Missing must-have technologies that interviewers will target.
   - **48-Hour Strategic Action Plan**: 2-3 high-impact concepts or portfolio features the candidate must review before interviewing.

6. **matchScore**:
   Rigorous integer 0 to 100:
   - 80-100: Strong candidate meeting all required tech + proven relevant scale.
   - 60-79: Promising candidate with transferable skills but notable gaps in 1-2 core technologies.
   - Below 60: Major mismatch in core stack or seniority bar.
   - If no resume provided, return 0.

7. **atsKeywords**:
   List the top 12-18 most critical ATS keywords from the JD (technologies, protocols, architecture patterns) needed for resume screening.

8. **matchedKeywords**:
   List of ATS keywords from the JD that the candidate ACTUALLY HAS in their provided resume (in skills, experiences, projects, or summary).

9. **missingKeywords**:
   List of critical ATS keywords from the JD that are COMPLETELY ABSENT from the candidate's resume.

---
### OUTPUT SCHEMA:

Return ONLY valid JSON with this exact structure:
{{
  "requiredSkills": [],
  "preferredSkills": [],
  "experienceLevel": "",
  "importantTopics": [],
  "atsKeywords": [],
  "matchedKeywords": [],
  "missingKeywords": [],
  "summary": "",
  "matchScore": 0
}}

Rules:
- Return ONLY valid JSON.
- No markdown wrappers outside JSON.
- No explanations.
- Follow the schema strictly.

Job Description:
{jd_text}
"""