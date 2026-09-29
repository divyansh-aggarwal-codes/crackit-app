import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api, { API_BASE_URL } from '../api/axios'
import Card from '../components/ui/Card'
import AiLoadingOverlay from '../components/AiLoadingOverlay'
import { JobDetailSkeleton } from '../components/Skeletons'
import { useToast } from '../components/ui/ToastProvider'

export default function JobDetail() {
  const { jobId } = useParams()
  const { showToast } = useToast()
  const [job, setJob] = useState(null)
  const [analysis, setAnalysis] = useState(null)
  const [tailored, setTailored] = useState(null)
  const [analyzeLoading, setAnalyzeLoading] = useState(false)
  const [tailorLoading, setTailorLoading] = useState(false)
  const [downloadLoading, setDownloadLoading] = useState(false)
  const [saveLoading, setSaveLoading] = useState(false)
  const [isEditing, setIsEditing] = useState(false)
  const [template, setTemplate] = useState('compact')
  const [pageLoading, setPageLoading] = useState(true)
  const navigate = useNavigate()
  const isMobile = window.innerWidth < 768

  useEffect(() => {
    const loadAll = async () => {
      setPageLoading(true)
      await api.get(`/api/jobs/${jobId}`).then(res => setJob(res.data))
      await Promise.all([
        api.get(`/api/ai/jobs/${jobId}/analysis`).then(res => setAnalysis(res.data)).catch(() => {}),
        api.get(`/api/ai/jobs/${jobId}/tailored-resume`).then(res => setTailored(res.data)).catch(() => {}),
      ])
      setPageLoading(false)
    }
    loadAll()
  }, [jobId])

  const handleAnalyze = async () => {
    setAnalyzeLoading(true)
    try {
      const res = await api.post(`/api/ai/jobs/${jobId}/analyze`)
      setAnalysis(res.data)
      showToast({ type: 'success', message: 'Job analysis completed!' })
    } catch (err) {
      showToast({ type: 'error', message: err.response?.data?.message || 'Analysis failed. Please try again.' })
    } finally {
      setAnalyzeLoading(false)
    }
  }

  const handleTailor = async () => {
    setTailorLoading(true)
    try {
      const res = await api.post(`/api/ai/jobs/${jobId}/tailor-resume`)
      setTailored(res.data)
      showToast({ type: 'success', message: 'Resume tailored successfully!' })
    } catch (err) {
      showToast({ type: 'error', message: err.response?.data?.message || 'Tailoring failed. Please try again.' })
    } finally {
      setTailorLoading(false)
    }
  }

  const handleSaveTailored = async () => {
    if (!tailored) return
    setSaveLoading(true)
    try {
      const res = await api.put(`/api/ai/jobs/${jobId}/tailored-resume`, tailored)
      setTailored(res.data)
      setIsEditing(false)
      showToast({ type: 'success', message: 'Tailored resume changes saved!' })
    } catch (err) {
      showToast({ type: 'error', message: err.response?.data?.message || 'Failed to save changes.' })
    } finally {
      setSaveLoading(false)
    }
  }

  const handleBulletChange = (expIndex, bulletIndex, newText) => {
    setTailored(prev => {
      const copy = JSON.parse(JSON.stringify(prev))
      copy.tailoredExperiences[expIndex].bullets[bulletIndex].bulletText = newText
      return copy
    })
  }

  const handleDeleteBullet = (expIndex, bulletIndex) => {
    setTailored(prev => {
      const copy = JSON.parse(JSON.stringify(prev))
      copy.tailoredExperiences[expIndex].bullets.splice(bulletIndex, 1)
      return copy
    })
  }

  const handleAddBullet = (expIndex) => {
    setTailored(prev => {
      const copy = JSON.parse(JSON.stringify(prev))
      if (!copy.tailoredExperiences[expIndex].bullets) {
        copy.tailoredExperiences[expIndex].bullets = []
      }
      copy.tailoredExperiences[expIndex].bullets.push({ bulletText: '', technologies: '' })
      return copy
    })
  }

  const handleDownloadTailored = async () => {
    setDownloadLoading(true)
    try {
      const token = localStorage.getItem('token')
      const res = await fetch(`${API_BASE_URL}/api/ai/jobs/${jobId}/tailored-resume/download?template=${template}`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      if (res.ok) {
        const blob = await res.blob()
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        const comp = (job?.companyName || '').trim().replace(/[^a-zA-Z0-9]/g, '_')
        const role = (job?.title || 'Resume').trim().replace(/[^a-zA-Z0-9]/g, '_')
        a.download = comp ? `Resume_${comp}_${role}.pdf` : `Resume_${role}.pdf`
        a.click()
        URL.revokeObjectURL(url)
        showToast({ type: 'success', message: 'Tailored resume downloaded!' })
      } else {
        showToast({ type: 'error', message: 'Download failed — make sure you have tailored this resume first' })
      }
    } catch {
      showToast({ type: 'error', message: 'Download failed. Please try again.' })
    } finally {
      setDownloadLoading(false)
    }
  }

  if (pageLoading) return <JobDetailSkeleton />

    
return (
  <div
    style={{
      width: '100%',
      maxWidth: 1200,
      margin: '0 auto'
    }}
  >
      {/* AI Overlays */}
      <AiLoadingOverlay type="analyze" visible={analyzeLoading} />
      <AiLoadingOverlay type="tailor" visible={tailorLoading} />

      {/* Header */}
<Card
  style={{
    marginBottom: 16,
    overflow: 'hidden',
    position: 'relative'
  }}
>
  {/* Top accent */}
  <div
    style={{
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      height: 5,
      background:
        'linear-gradient(90deg, #7c3aed, #a78bfa)'
    }}
  />

 <div
  style={{
   display: 'flex',
flexDirection: isMobile ? 'column' : 'row',
alignItems: isMobile ? 'flex-start' : 'center',
justifyContent: 'space-between',
gap: 18,
  }}
>
    <div style={{
      display: 'flex',
      gap: 14,
      minWidth: 0
    }}>
      {/* Avatar */}
      <div
        style={{
          width: 56,
          height: 56,

          borderRadius: 18,

          background:
            'linear-gradient(135deg, rgba(124,58,237,0.12), rgba(167,139,250,0.18))',

          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',

          fontSize: 22,
          fontWeight: 800,

          color: '#7c3aed',

          flexShrink: 0
        }}
      >
        {job.companyName?.charAt(0)?.toUpperCase()}
      </div>

      <div style={{ minWidth: 0 }}>
        <h1 style={{
          fontSize: 24,
          fontWeight: 700,
          color: '#1a1040',
          lineHeight: 1.25
        }}>
          {job.title}
        </h1>

        <p style={{
          fontSize: 14,
          color: '#7c6faa',
          marginTop: 6
        }}>
          {job.companyName}
          {job.location ? ` · ${job.location}` : ''}
        </p>

        <div style={{
          display: 'flex',
          gap: 8,
          marginTop: 14,
          flexWrap: 'wrap'
        }}>
          {job.experienceRequired && (
            <Chip>{job.experienceRequired}</Chip>
          )}

          {job.salaryRange && (
            <Chip>{job.salaryRange}</Chip>
          )}

          {job.source && (
            <Chip purple>{job.source}</Chip>
          )}
        </div>
      </div>
    </div>

    {/* Actions */}
   <div
  style={{
    display: 'flex',
    flexDirection: isMobile ? 'column' : 'row',
    gap: 10,
    width: isMobile ? '100%' : 'auto',
    justifyContent: isMobile
      ? 'stretch'
      : 'flex-end'
  }}
>
      <button
        onClick={handleAnalyze}
        disabled={analyzeLoading || tailorLoading}
       style={{
  ...outlineBtnStyle,
  width: isMobile ? '100%' : 'auto',
  justifyContent: 'center'
}}
      >
        <i className="ti ti-brain" style={{ fontSize: 13 }} />
        Analyse JD
      </button>

      <button
        onClick={handleTailor}
        disabled={tailorLoading || analyzeLoading}
        style={{
  ...btnStyle,
  width: isMobile ? '100%' : 'auto',
  justifyContent: 'center'
}}
      >
        <i className="ti ti-wand" style={{ fontSize: 13 }} />
        Tailor Resume
      </button>

      <button
        onClick={() => navigate(`/jobs/${jobId}/prep`)}
        style={{
  ...outlineBtnStyle,
  width: isMobile ? '100%' : 'auto',
  justifyContent: 'center'
}}
      >
        <i className="ti ti-message-question" style={{ fontSize: 13 }} />
        Interview Prep
      </button>
    </div>
  </div>
</Card>

      {/* JD Analysis */}
      {analysis && (
        <Card style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: '#1a1040' }}>JD Analysis</div>
            {analysis.matchScore != null && (
              <div style={{ fontSize: 12, color: '#7c6faa' }}>
                Raw match: <span style={{ fontWeight: 600, color: '#5b21b6' }}>{analysis.matchScore}%</span>
              </div>
            )}
          </div>

          {analysis.matchedKeywords?.length > 0 && (
            <Section label={`✓ Matched In Your Resume (${analysis.matchedKeywords.length})`}>
              {analysis.matchedKeywords.map(k => <Chip key={k} success><i className="ti ti-check" style={{ fontSize: 11 }} /> {k}</Chip>)}
            </Section>
          )}

          {analysis.missingKeywords?.length > 0 && (
            <Section label={`⚠ Missing From Your Resume (${analysis.missingKeywords.length})`}>
              {analysis.missingKeywords.map(k => <Chip key={k} danger><i className="ti ti-alert-triangle" style={{ fontSize: 11 }} /> {k}</Chip>)}
            </Section>
          )}

          <Section label="Required Skills">
            {analysis.requiredSkills?.map(s => <Chip key={s} purple>{s}</Chip>)}
          </Section>
          <Section label="Preferred Skills">
            {analysis.preferredSkills?.map(s => <Chip key={s}>{s}</Chip>)}
          </Section>
          <Section label="All ATS Keywords">
            {analysis.atsKeywords?.map(k => <Chip key={k} light>{k}</Chip>)}
          </Section>
          {analysis.aiSummary && (
            <div style={{ marginTop: 14 }}>
              <div style={sectionLabelStyle}>Summary</div>
              <p style={{ fontSize: 13, color: '#4b3f72', lineHeight: 1.6 }}>{analysis.aiSummary}</p>
            </div>
          )}
        </Card>
      )}

      {/* Tailored Resume */}
      {tailored && (
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
            <div>
              <div style={{ fontSize: 20, fontWeight: 700, color: '#1a1040' }}>Tailored Resume</div>
              {tailored.matchScore != null && (
                <div style={{ fontSize: 12, color: '#7c6faa', marginTop: 2 }}>
                  Tailored match: <span style={{ fontWeight: 600, color: '#059669' }}>{tailored.matchScore}%</span>
                </div>
              )}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <select
                value={template}
                onChange={(e) => setTemplate(e.target.value)}
                style={{
                  fontSize: 12,
                  padding: '7px 10px',
                  borderRadius: 10,
                  border: '1px solid #e4daff',
                  background: '#fcfaff',
                  color: '#1a1040',
                  fontWeight: 600,
                  outline: 'none',
                  cursor: 'pointer'
                }}
              >
                <option value="compact">📄 Compact 1-Page (Strict ATS)</option>
                <option value="classic">Classic Clean</option>
                <option value="modern">Modern Purple</option>
              </select>

              <button
                onClick={() => {
                  if (isEditing) handleSaveTailored()
                  else setIsEditing(true)
                }}
                disabled={saveLoading}
                style={{
                  ...outlineBtnStyle,
                  gap: 6,
                  color: isEditing ? '#059669' : '#7c3aed',
                  borderColor: isEditing ? '#10b981' : '#e4daff',
                  background: isEditing ? '#ecfdf5' : '#fff'
                }}
              >
                {saveLoading
                  ? <><i className="ti ti-loader-2 ti-spin" style={{ fontSize: 13 }} /> Saving...</>
                  : isEditing
                  ? <><i className="ti ti-check" style={{ fontSize: 13 }} /> Save Edits</>
                  : <><i className="ti ti-pencil" style={{ fontSize: 13 }} /> Edit Bullets</>
                }
              </button>

              <button onClick={handleDownloadTailored} disabled={downloadLoading} style={{ ...btnStyle, gap: 6 }}>
                {downloadLoading
                  ? <><i className="ti ti-loader-2 ti-spin" style={{ fontSize: 13 }} /> Generating...</>
                  : <><i className="ti ti-download" style={{ fontSize: 13 }} /> Download PDF</>
                }
              </button>
            </div>
          </div>

          {tailored.tailoredSummary && (
            <div style={{ marginBottom: 16 }}>
              <div style={sectionLabelStyle}>Summary</div>
              {isEditing ? (
                <textarea
                  value={tailored.tailoredSummary}
                  onChange={(e) => setTailored(prev => ({ ...prev, tailoredSummary: e.target.value }))}
                  style={{
                    width: '100%',
                    minHeight: 80,
                    padding: 10,
                    borderRadius: 8,
                    border: '1.5px solid #d8b4fe',
                    fontFamily: 'inherit',
                    fontSize: 13,
                    color: '#1a1040',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              ) : (
                <p style={{ fontSize: 13, color: '#4b3f72', lineHeight: 1.6 }}>{tailored.tailoredSummary}</p>
              )}
            </div>
          )}

          <Section label="Curated Skills">
            {tailored.tailoredSkills?.map(s => <Chip key={s} purple>{s}</Chip>)}
          </Section>

          <div style={{ marginTop: 14 }}>
            <div style={sectionLabelStyle}>Experience (Google X-Y-Z Formatted)</div>
            {tailored.tailoredExperiences?.map((exp, expIdx) => (
              <div key={expIdx} style={{ marginBottom: 16, padding: '12px 14px', borderRadius: 12, background: '#faf9ff', border: '1px solid #ede9fe' }}>
                <div style={{ fontSize: 14, fontWeight: 700, color: '#1a1040', marginBottom: 8 }}>
                  {exp.role} — <span style={{ color: '#6d28d9' }}>{exp.companyName}</span>
                </div>

                <ul style={{ paddingLeft: 0, listStyle: 'none', margin: 0 }}>
                  {exp.bullets?.map((b, bIdx) => (
                    <li key={bIdx} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                      <span style={{ marginTop: 7, width: 5, height: 5, borderRadius: '50%', background: '#a78bfa', flexShrink: 0 }} />
                      {isEditing ? (
                        <div style={{ display: 'flex', flex: 1, gap: 8, alignItems: 'center' }}>
                          <textarea
                            value={b.bulletText}
                            onChange={(e) => handleBulletChange(expIdx, bIdx, e.target.value)}
                            style={{
                              flex: 1,
                              minHeight: 52,
                              padding: 8,
                              fontSize: 12,
                              color: '#1a1040',
                              borderRadius: 8,
                              border: '1px solid #d8b4fe',
                              fontFamily: 'inherit',
                              outline: 'none',
                              boxSizing: 'border-box'
                            }}
                          />
                          <button
                            type="button"
                            onClick={() => handleDeleteBullet(expIdx, bIdx)}
                            title="Delete bullet"
                            style={{
                              background: '#fee2e2',
                              border: 'none',
                              color: '#ef4444',
                              borderRadius: 8,
                              padding: '6px 8px',
                              cursor: 'pointer'
                            }}
                          >
                            <i className="ti ti-trash" style={{ fontSize: 14 }} />
                          </button>
                        </div>
                      ) : (
                        <div style={{ fontSize: 12.5, color: '#334155', lineHeight: 1.55 }}>
                          {b.bulletText}
                        </div>
                      )}
                    </li>
                  ))}
                </ul>

                {isEditing && (
                  <button
                    type="button"
                    onClick={() => handleAddBullet(expIdx)}
                    style={{
                      marginTop: 6,
                      background: 'none',
                      border: '1px dashed #a78bfa',
                      borderRadius: 8,
                      padding: '4px 10px',
                      color: '#7c3aed',
                      fontSize: 11,
                      fontWeight: 600,
                      cursor: 'pointer'
                    }}
                  >
                    + Add Bullet
                  </button>
                )}
              </div>
            ))}
          </div>

          <div style={{ marginTop: 14 }}>
            <div style={sectionLabelStyle}>Projects</div>
            {tailored.tailoredProjects?.map((p, i) => (
              <div key={i} style={{ marginBottom: 12, padding: '10px 12px', borderRadius: 10, background: '#faf9ff', border: '1px solid #ede9fe' }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: '#1a1040' }}>{p.title}</div>
                {p.techStack && <div style={{ fontSize: 11, color: '#7c3aed', marginTop: 2, fontWeight: 600 }}>{p.techStack}</div>}
                <div style={{ fontSize: 12, color: '#4b3f72', marginTop: 3 }}>{p.description}</div>
              </div>
            ))}
          </div>

          <Section label="ATS Keywords Used">
            {tailored.atsKeywordsUsed?.map(k => <Chip key={k} light>{k}</Chip>)}
          </Section>
        </Card>
      )}
    </div>
  )
}

function Section({ label, children }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={sectionLabelStyle}>{label}</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>{children}</div>
    </div>
  )
}

function Chip({ children, purple, light, success, danger }) {
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 700,
        padding: '5px 10px',
        borderRadius: 999,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        background:
          success ? '#ecfdf5' :
          danger ? '#fef2f2' :
          purple ? '#ede9fe' :
          light ? '#f5f3ff' : '#f0eeff',
        color:
          success ? '#047857' :
          danger ? '#b91c1c' :
          purple ? '#5b21b6' : '#6d28d9',
        border: `1px solid ${
          success ? 'rgba(16, 185, 129, 0.25)' :
          danger ? 'rgba(239, 68, 68, 0.25)' :
          'rgba(124,58,237,0.08)'
        }`
      }}
    >
      {children}
    </span>
  )
}

const sectionLabelStyle = { fontSize: 11, fontWeight: 700, color: '#a094c4', letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 8 }
const btnStyle = {
  display: 'flex', alignItems: 'center', gap: 6,
  padding: '8px 14px', borderRadius: 10,
  background: '#7c3aed', color: '#fff', border: 'none',
  fontSize: 12, fontWeight: 600, cursor: 'pointer'
}
const outlineBtnStyle = {
  display: 'flex', alignItems: 'center', gap: 6,
  padding: '8px 14px', borderRadius: 10,
  background: '#fff', color: '#7c3aed',
  border: '1px solid #e4daff',
  fontSize: 12, fontWeight: 600, cursor: 'pointer'
}