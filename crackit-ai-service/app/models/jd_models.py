from pydantic import BaseModel
from typing import List, Optional


class BulletPoint(BaseModel):
    bulletText: str
    technologies: str


class ResumeExperience(BaseModel):
    companyName: str
    role: str
    description: str
    bullets: List[BulletPoint] = []


class ResumeSkill(BaseModel):
    skillName: str
    category: str
    proficiencyLevel: str
    yearsUsed: int


class ResumeProject(BaseModel):
    title: str
    description: str
    techStack: str
    impactMetrics: str


class JDAnalysisRequest(BaseModel):
    jdText: str
    summary: str = ""
    skills: List[ResumeSkill] = []
    experiences: List[ResumeExperience] = []
    projects: List[ResumeProject] = []


class JDAnalysisResponse(BaseModel):
    requiredSkills: List[str] = []
    preferredSkills: List[str] = []
    experienceLevel: str = ""
    importantTopics: List[str] = []
    atsKeywords: List[str] = []
    matchedKeywords: List[str] = []
    missingKeywords: List[str] = []
    summary: str = ""
    matchScore: int = 0