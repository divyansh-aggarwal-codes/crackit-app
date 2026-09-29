from datetime import datetime
from typing import Any
import re
from weasyprint import HTML


class ResumePdfService:

    ATS_CATEGORY_PRIORITY = {
        "language": 1,
        "backend": 2,
        "framework": 2,
        "database": 3,
        "sql": 3,
        "caching": 4,
        "reliability": 4,
        "messaging": 5,
        "distributed": 5,
        "stream": 5,
        "kafka": 5,
        "cloud": 6,
        "devops": 6,
        "aws": 6,
        "docker": 6,
        "tool": 7,
        "testing": 7,
        "test": 7,
        "git": 7,
        "concept": 8,
        "architecture": 8,
        "design": 8,
    }

    def generate_pdf(self, data: Any, template: str = "classic") -> bytes:
        if not isinstance(data, dict):
            if hasattr(data, "model_dump"):
                data = data.model_dump()
            elif hasattr(data, "dict"):
                data = data.dict()
            else:
                data = dict(data)
        t = (template or "classic").lower().strip()
        if t == "modern":
            html = self._build_modern(data)
        elif t == "compact":
            html = self._build_compact(data)
        else:
            html = self._build_classic(data)
        return HTML(string=html).write_pdf()

    # ─── Shared helpers ──────────────────────────────────────────────────────

    def _fmt_date(self, date_str: str) -> str:
        if not date_str:
            return ""
        try:
            dt = datetime.strptime(date_str[:10], "%Y-%m-%d")
            return dt.strftime("%b %Y")
        except:
            return date_str

    def _contact_parts(self, d: dict) -> list:
        parts = []
        if d.get("email"):    parts.append(d["email"])
        if d.get("phone"):    parts.append(d["phone"])
        if d.get("location"): parts.append(d["location"])
        if d.get("linkedinUrl"): parts.append(f'<a href="{d["linkedinUrl"]}">LinkedIn</a>')
        if d.get("githubUrl"):   parts.append(f'<a href="{d["githubUrl"]}">GitHub</a>')
        return parts

    def _category_priority(self, cat: str) -> int:
        c = (cat or "").lower().strip()
        for key, prio in self.ATS_CATEGORY_PRIORITY.items():
            if key in c:
                return prio
        if not c or c == "other":
            return 99
        return 20

    def _skills_by_category(self, skills: list) -> list:
        """
        Groups skills by category and returns sorted tuples of (category_name, [skills])
        strictly according to ATS precedence: Languages -> Backend -> Databases -> Caching -> Messaging -> Cloud -> Tools -> Concepts
        """
        grouped: dict[str, list] = {}
        for s in skills:
            if isinstance(s, dict):
                cat = (s.get("category") or "").strip() or "Other"
                name = (s.get("skillName") or "").strip()
            else:
                cat = "Other"
                name = str(s).strip()
            if name:
                grouped.setdefault(cat, []).append(name)

        # Sort categories based on ATS priority
        sorted_cats = sorted(grouped.keys(), key=lambda c: (self._category_priority(c), c.lower()))
        return [(cat, grouped[cat]) for cat in sorted_cats if grouped[cat]]

    def _exp_html(self, experiences: list, max_bullets: int = None) -> str:
        html = ""
        for exp in experiences:
            start = self._fmt_date(exp.get("startDate", ""))
            end = "Present" if exp.get("currentCompany") or exp.get("current") else self._fmt_date(exp.get("endDate", ""))
            date_range = f"{start} – {end}" if start else ""
            location = exp.get("location", "")
            role = exp.get("role", exp.get("jobTitle", ""))
            company = exp.get("companyName", exp.get("company", ""))
            sub_parts = [company]
            if location:
                sub_parts.append(location)
            sub_text = " · ".join(filter(None, sub_parts))

            bullets_html = ""
            raw_bullets = exp.get("bullets", [])
            if max_bullets and len(raw_bullets) > max_bullets:
                raw_bullets = raw_bullets[:max_bullets]

            for b in raw_bullets:
                text = b.get("bulletText", "") if isinstance(b, dict) else str(b)
                if text and text.strip():
                    bullets_html += f"<li>{text.strip()}</li>"

            # Fallback if bullets was empty but description was provided
            if not bullets_html and exp.get("description"):
                desc_lines = [l.strip().lstrip("•-* ").strip() for l in exp["description"].replace("\r", "").split("\n") if l.strip()]
                if max_bullets and len(desc_lines) > max_bullets:
                    desc_lines = desc_lines[:max_bullets]
                for l in desc_lines:
                    bullets_html += f"<li>{l}</li>"

            html += f"""
            <div class="entry">
                <div class="entry-header">
                    <span class="entry-title">{role}</span>
                    <span class="entry-date">{date_range}</span>
                </div>
                {f'<div class="entry-sub">{sub_text}</div>' if sub_text else ''}
                {"<ul>" + bullets_html + "</ul>" if bullets_html else ""}
            </div>
            """
        return html

    def _proj_html(self, projects: list, max_projects: int = None, max_bullets: int = None) -> str:
        html = ""
        projs = projects[:max_projects] if max_projects else projects
        for p in projs:
            name = p.get("title", p.get("projectName", ""))
            tech = p.get("techStack", "")
            desc = p.get("description", "")
            impact = p.get("impactMetrics", "")

            bullets_html = ""
            # 1. Use explicit bullets if present
            raw_bullets = p.get("bullets", [])
            if max_bullets and len(raw_bullets) > max_bullets:
                raw_bullets = raw_bullets[:max_bullets]

            if raw_bullets:
                for b in raw_bullets:
                    text = b.get("bulletText", "") if isinstance(b, dict) else str(b)
                    if text and text.strip():
                        bullets_html += f"<li>{text.strip()}</li>"

            # 2. If no bullets, intelligently convert project description into bullets
            if not bullets_html and desc:
                raw_lines = [l.strip().lstrip("•-* ").strip() for l in desc.replace("\r", "").split("\n") if l.strip()]
                if len(raw_lines) == 1:
                    sentences = [s.strip().lstrip("•-* ").strip() for s in re.split(r'(?<=[.!?])\s+', raw_lines[0]) if len(s.strip()) > 15]
                    raw_lines = sentences if len(sentences) > 1 else raw_lines
                if max_bullets and len(raw_lines) > max_bullets:
                    raw_lines = raw_lines[:max_bullets]
                for l in raw_lines:
                    bullets_html += f"<li>{l}</li>"

            # 3. Add impact metrics if specified and not already in bullets
            if impact and not any(impact.lower() in str(b).lower() for b in raw_bullets):
                bullets_html += f"<li><strong>Key Impact:</strong> {impact}</li>"

            html += f"""
            <div class="entry">
                <div class="entry-header">
                    <span class="entry-title">{name}</span>
                </div>
                {f'<div class="entry-tech">{tech}</div>' if tech else ''}
                {"<ul>" + bullets_html + "</ul>" if bullets_html else ""}
            </div>
            """
        return html

    def _edu_html(self, education) -> str:
        if not education:
            return ""
        html = ""
        items = education if isinstance(education, list) else [education]
        for edu in items:
            if isinstance(edu, dict):
                degree = edu.get("degree", edu.get("course", ""))
                institution = edu.get("institution", edu.get("college", edu.get("university", "")))
                year = edu.get("year", edu.get("dates", edu.get("endDate", "")))
                score = edu.get("score", edu.get("gpa", edu.get("percentage", "")))

                sub_parts = [institution]
                if score:
                    sub_parts.append(str(score))
                sub_text = " · ".join(filter(None, sub_parts))

                html += f"""
                <div class="entry">
                    <div class="entry-header">
                        <span class="entry-title">{degree}</span>
                        <span class="entry-date">{year}</span>
                    </div>
                    {f'<div class="entry-sub">{sub_text}</div>' if sub_text else ''}
                </div>
                """
            elif isinstance(edu, str) and edu.strip():
                html += f"""
                <div class="entry">
                    <div class="entry-desc">{edu.strip()}</div>
                </div>
                """
        return html

    # ─── Template 1: Classic (Clean, True ATS-Friendly Single-Column) ─────────

    def _build_classic(self, d: dict) -> str:
        full_name   = d.get("fullName", "")
        summary     = d.get("summary", "")
        skills      = d.get("skills", [])
        experiences = d.get("experiences", [])
        projects    = d.get("projects", [])
        education   = d.get("education") or d.get("educations") or d.get("educationList")

        contact_parts = self._contact_parts(d)
        contact_html  = " · ".join(contact_parts)

        # Sorted Skills grouped strictly by ATS precedence
        sorted_skill_tuples = self._skills_by_category(skills)
        skills_html = ""
        for cat, names in sorted_skill_tuples:
            skills_html += f'<div class="skill-row"><span class="skill-cat">{cat}:</span> <span class="skill-val">{", ".join(names)}</span></div>'

        exp_html  = self._exp_html(experiences)
        proj_html = self._proj_html(projects)
        edu_html  = self._edu_html(education)

        def section(title, body):
            if not body or not body.strip():
                return ""
            return f"""
            <div class="section">
                <div class="section-title">{title}</div>
                {body}
            </div>"""

        return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
  @page {{
    size: A4;
    margin: 28pt 36pt;
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
    font-size: 9.5pt;
    color: #111827;
    line-height: 1.45;
  }}

  /* Header */
  .header {{
    margin-bottom: 12pt;
    padding-bottom: 8pt;
  }}
  .name {{
    font-size: 20pt;
    font-weight: 800;
    color: #0f172a;
    letter-spacing: -0.4pt;
    margin-bottom: 4pt;
  }}
  .contact {{
    font-size: 9pt;
    color: #475569;
  }}
  .contact a {{
    color: #0f172a;
    text-decoration: none;
    font-weight: 600;
  }}

  /* Sections */
  .section {{
    margin-bottom: 11pt;
  }}
  .section-title {{
    font-size: 9pt;
    font-weight: 800;
    color: #0f172a;
    text-transform: uppercase;
    letter-spacing: 0.8pt;
    border-bottom: 1pt solid #0f172a;
    padding-bottom: 2pt;
    margin-bottom: 6pt;
  }}

  /* Summary */
  .summary-text {{
    font-size: 9pt;
    color: #334155;
    line-height: 1.5;
    text-align: justify;
  }}

  /* Skills */
  .skill-row {{
    font-size: 9pt;
    color: #334155;
    margin-bottom: 2.5pt;
    line-height: 1.4;
  }}
  .skill-cat {{
    font-weight: 700;
    color: #0f172a;
  }}
  .skill-val {{
    color: #334155;
  }}

  /* Entries */
  .entry {{
    margin-bottom: 8.5pt;
    page-break-inside: avoid;
  }}
  .entry-header {{
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 1pt;
  }}
  .entry-title {{
    font-size: 9.5pt;
    font-weight: 700;
    color: #0f172a;
  }}
  .entry-date {{
    font-size: 8.5pt;
    font-weight: 600;
    color: #475569;
    white-space: nowrap;
    text-align: right;
  }}
  .entry-sub {{
    font-size: 8.5pt;
    font-weight: 500;
    color: #475569;
    margin-bottom: 2pt;
  }}
  .entry-tech {{
    font-size: 8.5pt;
    font-style: italic;
    color: #475569;
    margin-bottom: 2.5pt;
  }}
  .entry-desc {{
    font-size: 9pt;
    color: #334155;
    line-height: 1.45;
  }}

  ul {{
    margin-top: 2pt;
    margin-left: 15pt;
    list-style-type: disc;
  }}
  ul li {{
    font-size: 9pt;
    color: #1e293b;
    margin-bottom: 2.5pt;
    line-height: 1.45;
    text-align: justify;
  }}
</style>
</head>
<body>

<div class="header">
  <div class="name">{full_name}</div>
  <div class="contact">{contact_html}</div>
</div>

{section("Professional Summary", f"<p class='summary-text'>{summary}</p>" if summary else "")}
{section("Skills", skills_html)}
{section("Experience", exp_html)}
{section("Projects", proj_html)}
{section("Education", edu_html)}

</body>
</html>"""

    # ─── Template 2: Modern (Sleek Purple Accent, Still 100% ATS-Compliant) ─

    def _build_modern(self, d: dict) -> str:
        full_name   = d.get("fullName", "")
        summary     = d.get("summary", "")
        skills      = d.get("skills", [])
        experiences = d.get("experiences", [])
        projects    = d.get("projects", [])
        education   = d.get("education") or d.get("educations") or d.get("educationList")

        contact_parts = self._contact_parts(d)
        contact_html  = " · ".join(contact_parts)

        sorted_skill_tuples = self._skills_by_category(skills)
        skills_html = ""
        for cat, names in sorted_skill_tuples:
            skills_html += f'<div class="skill-row"><span class="skill-cat">{cat}:</span> <span class="skill-val">{", ".join(names)}</span></div>'

        exp_html  = self._exp_html(experiences)
        proj_html = self._proj_html(projects)
        edu_html  = self._edu_html(education)

        def section(title, body):
            if not body or not body.strip():
                return ""
            return f"""
            <div class="section">
                <div class="section-title">{title}</div>
                {body}
            </div>"""

        return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
  @page {{
    size: A4;
    margin: 28pt 36pt;
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
    font-size: 9.5pt;
    color: #111827;
    line-height: 1.45;
  }}

  /* Header */
  .header {{
    margin-bottom: 14pt;
    padding-bottom: 8pt;
    border-bottom: 2pt solid #6d28d9;
  }}
  .name {{
    font-size: 22pt;
    font-weight: 800;
    color: #1e1b4b;
    letter-spacing: -0.4pt;
    margin-bottom: 4pt;
  }}
  .contact {{
    font-size: 9pt;
    color: #475569;
  }}
  .contact a {{
    color: #6d28d9;
    text-decoration: none;
    font-weight: 600;
  }}

  /* Sections */
  .section {{
    margin-bottom: 12pt;
  }}
  .section-title {{
    font-size: 9pt;
    font-weight: 800;
    color: #4c1d95;
    text-transform: uppercase;
    letter-spacing: 0.8pt;
    border-bottom: 1pt solid #ddd6fe;
    padding-bottom: 2pt;
    margin-bottom: 6pt;
  }}

  /* Summary */
  .summary-text {{
    font-size: 9pt;
    color: #334155;
    line-height: 1.5;
    text-align: justify;
  }}

  /* Skills */
  .skill-row {{
    font-size: 9pt;
    color: #334155;
    margin-bottom: 2.5pt;
    line-height: 1.4;
  }}
  .skill-cat {{
    font-weight: 700;
    color: #4c1d95;
  }}
  .skill-val {{
    color: #334155;
  }}

  /* Entries */
  .entry {{
    margin-bottom: 8.5pt;
    page-break-inside: avoid;
  }}
  .entry-header {{
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 1pt;
  }}
  .entry-title {{
    font-size: 9.5pt;
    font-weight: 700;
    color: #1e1b4b;
  }}
  .entry-date {{
    font-size: 8.5pt;
    font-weight: 600;
    color: #6d28d9;
    white-space: nowrap;
    text-align: right;
  }}
  .entry-sub {{
    font-size: 8.5pt;
    font-weight: 600;
    color: #475569;
    margin-bottom: 2pt;
  }}
  .entry-tech {{
    font-size: 8.5pt;
    font-style: italic;
    color: #6d28d9;
    margin-bottom: 2.5pt;
  }}
  .entry-desc {{
    font-size: 9pt;
    color: #334155;
    line-height: 1.45;
  }}

  ul {{
    margin-top: 2pt;
    margin-left: 15pt;
    list-style-type: disc;
  }}
  ul li {{
    font-size: 9pt;
    color: #1e293b;
    margin-bottom: 2.5pt;
    line-height: 1.45;
    text-align: justify;
  }}
  ul li::marker {{
    color: #6d28d9;
  }}
</style>
</head>
<body>

<div class="header">
  <div class="name">{full_name}</div>
  <div class="contact">{contact_html}</div>
</div>

{section("Professional Summary", f"<p class='summary-text'>{summary}</p>" if summary else "")}
{section("Skills", skills_html)}
{section("Experience", exp_html)}
{section("Projects", proj_html)}
{section("Education", edu_html)}

</body>
</html>"""

    # ─── Template 3: Compact (Strict 1-Page High-Density Executive Layout) ───

    def _build_compact(self, d: dict) -> str:
        full_name   = d.get("fullName", "")
        summary     = d.get("summary", "")
        skills      = d.get("skills", [])
        experiences = d.get("experiences", [])
        projects    = d.get("projects", [])
        education   = d.get("education") or d.get("educations") or d.get("educationList")

        contact_parts = self._contact_parts(d)
        contact_html  = " · ".join(contact_parts)

        sorted_skill_tuples = self._skills_by_category(skills)
        skills_html = ""
        for cat, names in sorted_skill_tuples:
            skills_html += f'<div class="skill-row"><span class="skill-cat">{cat}:</span> <span class="skill-val">{", ".join(names)}</span></div>'

        # Strict 1-page limits: cap bullets so entire resume fits comfortably on single page
        max_exp_bullets = 2 if len(experiences) >= 3 else 3
        exp_html  = self._exp_html(experiences, max_bullets=max_exp_bullets)
        proj_html = self._proj_html(projects, max_projects=2, max_bullets=2)
        edu_html  = self._edu_html(education)

        def section(title, body):
            if not body or not body.strip():
                return ""
            return f"""
            <div class="section">
                <div class="section-title">{title}</div>
                {body}
            </div>"""

        return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
  @page {{
    size: A4;
    margin: 18pt 28pt;
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
    font-size: 8.5pt;
    color: #0f172a;
    line-height: 1.32;
  }}

  /* Header */
  .header {{
    margin-bottom: 8pt;
    padding-bottom: 4pt;
    text-align: center;
  }}
  .name {{
    font-size: 18pt;
    font-weight: 800;
    color: #0f172a;
    letter-spacing: -0.3pt;
    margin-bottom: 2pt;
    text-transform: uppercase;
  }}
  .contact {{
    font-size: 8pt;
    color: #475569;
  }}
  .contact a {{
    color: #0f172a;
    text-decoration: none;
    font-weight: 600;
  }}

  /* Sections */
  .section {{
    margin-bottom: 7pt;
    page-break-inside: avoid;
  }}
  .section-title {{
    font-size: 8.5pt;
    font-weight: 800;
    color: #0f172a;
    text-transform: uppercase;
    letter-spacing: 0.8pt;
    border-bottom: 1pt solid #0f172a;
    padding-bottom: 1.5pt;
    margin-bottom: 4pt;
  }}

  /* Summary */
  .summary-text {{
    font-size: 8.5pt;
    color: #334155;
    line-height: 1.34;
    text-align: justify;
  }}

  /* Skills */
  .skill-row {{
    font-size: 8.5pt;
    color: #334155;
    margin-bottom: 1.5pt;
    line-height: 1.3;
  }}
  .skill-cat {{
    font-weight: 700;
    color: #0f172a;
  }}
  .skill-val {{
    color: #334155;
  }}

  /* Entries */
  .entry {{
    margin-bottom: 5pt;
    page-break-inside: avoid;
  }}
  .entry-header {{
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 0.5pt;
  }}
  .entry-title {{
    font-size: 8.5pt;
    font-weight: 700;
    color: #0f172a;
  }}
  .entry-date {{
    font-size: 8pt;
    font-weight: 600;
    color: #475569;
    white-space: nowrap;
    text-align: right;
  }}
  .entry-sub {{
    font-size: 8pt;
    font-weight: 600;
    color: #475569;
    margin-bottom: 1pt;
  }}
  .entry-tech {{
    font-size: 8pt;
    font-style: italic;
    color: #475569;
    margin-bottom: 1.5pt;
  }}
  .entry-desc {{
    font-size: 8.5pt;
    color: #334155;
    line-height: 1.32;
  }}

  ul {{
    margin-top: 1pt;
    margin-left: 12pt;
    list-style-type: disc;
  }}
  ul li {{
    font-size: 8.5pt;
    color: #1e293b;
    margin-bottom: 1.5pt;
    line-height: 1.32;
    text-align: justify;
  }}
</style>
</head>
<body>

<div class="header">
  <div class="name">{full_name}</div>
  <div class="contact">{contact_html}</div>
</div>

{section("Professional Summary", f"<p class='summary-text'>{summary}</p>" if summary else "")}
{section("Skills", skills_html)}
{section("Experience", exp_html)}
{section("Projects", proj_html)}
{section("Education", edu_html)}

</body>
</html>"""