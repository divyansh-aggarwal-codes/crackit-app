from pydantic import BaseModel
from typing import List, Optional, Any


# ── Tailoring models (existing) ──────────────────────────
class BulletPoint(BaseModel):
    bulletText: str = ""
    technologies: str = ""


class TailoredExperience(BaseModel):
    companyName: str = ""
    role: str = ""
    bullets: List[BulletPoint] = []


class TailoredProject(BaseModel):
    title: str = ""
    description: str = ""
    techStack: str = ""
    impactMetrics: str = ""
    bullets: List[BulletPoint] = []


class ResumeTailoringRequest(BaseModel):
    jdAnalysis: dict
    summary: str = ""
    skills: List[dict] = []
    experiences: List[dict] = []
    projects: List[dict] = []


class ResumeTailoringResponse(BaseModel):
    tailoredSummary: str = ""
    tailoredSkills: List[str] = []
    tailoredExperiences: List[TailoredExperience] = []
    tailoredProjects: List[TailoredProject] = []
    atsKeywordsUsed: List[str] = []
    matchScore: int = 0


# ── Resume parsing models ─────────────────────────────────
class ParsedBullet(BaseModel):
    bulletText: str = ""
    technologies: str = ""


class ParsedExperience(BaseModel):
    companyName: str = ""
    role: str = ""
    startDate: Optional[str] = None
    endDate: Optional[str] = None
    currentCompany: bool = False
    description: str = ""
    bullets: List[Any] = []


class ParsedSkill(BaseModel):
    skillName: str = ""
    category: str = ""
    proficiencyLevel: str = ""
    yearsUsed: int = 0


class ParsedProject(BaseModel):
    title: str = ""
    description: str = ""
    techStack: str = ""
    githubUrl: str = ""
    impactMetrics: str = ""
    bullets: List[Any] = []


class ParsedEducation(BaseModel):
    degree: str = ""
    institution: str = ""
    year: str = ""
    score: str = ""


class ParsedResume(BaseModel):
    summary: str = ""
    skills: List[ParsedSkill] = []
    experiences: List[ParsedExperience] = []
    projects: List[ParsedProject] = []
    education: List[ParsedEducation] = []


# ── PDF generation models ─────────────────────────────────
class ResumePdfRequest(BaseModel):
    fullName: str = "guest"
    email: str = ""
    phone: str = ""
    location: str = ""
    linkedinUrl: str = ""
    githubUrl: str = ""
    summary: str = ""
    skills: List[dict] = []
    experiences: List[dict] = []
    projects: List[dict] = []
    education: Optional[Any] = None
    photoBase64: Optional[str] = None
    template: Optional[str] = "classic"


# ── Bullet enhancer models ─────────────────────────────────
class BulletFormulaBreakdown(BaseModel):
    accomplished_x: str = ""
    measured_by_y: str = ""
    doing_z: str = ""


class BulletAlternative(BaseModel):
    angle: str = ""
    bullet: str = ""


class BulletEnhanceRequest(BaseModel):
    bulletText: str
    role: Optional[str] = ""
    company: Optional[str] = ""
    techStack: Optional[str] = ""


class BulletEnhanceResponse(BaseModel):
    original_bullet: str = ""
    enhanced_bullet: str = ""
    formula_breakdown: Optional[BulletFormulaBreakdown] = None
    action_verb: str = ""
    metric_dimension: str = ""
    strengths: List[str] = []
    alternatives: List[BulletAlternative] = []