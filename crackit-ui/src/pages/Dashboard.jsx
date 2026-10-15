import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import api from '../api/axios'
import Card from '../components/ui/Card'
import StatusBadge from '../components/ui/StatusBadge'
import DonutChart from '../components/DonutChart'
import QuickScanModal from '../components/QuickScanModal'
import ApplyConfirmModal from '../components/tracker/ApplyConfirmModal'
import { useToast } from '../components/ui/ToastProvider'
import { DashboardSkeleton } from '../components/Skeletons'
import AnimatedNumber from '../components/ui/AnimatedNumber'
import PageHeader from '../components/ui/PageHeader'

export default function Dashboard() {
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)
  const { user } = useAuth()
  const { showToast } = useToast()
  const [showScan, setShowScan] = useState(false)
  const [resumeStrength, setResumeStrength] = useState(null)
  const [recommendedJobs, setRecommendedJobs] = useState([])
  const [recLoading, setRecLoading] = useState(true)
  const [currentRoadmap, setCurrentRoadmap] = useState(null)
  const [animateBars, setAnimateBars] = useState(false)
  const [applyingJob, setApplyingJob] = useState(null)
  const navigate = useNavigate()

  const [screenWidth, setScreenWidth] = useState(window.innerWidth)

  const fetchDashboard = () => {
    api.get('/api/tracker/dashboard')
      .then(res => setDashboard(res.data))
      .catch(() => {})
  }

  const handleApplyClick = (job) => {
    const url = job.url || job.applyUrl
    if (url) {
      window.open(url, '_blank')
    }
    setApplyingJob(job)
  }

  const handleTrackSuccess = (trackedApp) => {
    fetchDashboard()
    showToast({
      type: 'success',
      message: `Added ${trackedApp?.companyName || 'job'} to your pipeline as APPLIED! 🚀`
    })
  }

useEffect(() => {
  const handleResize = () => setScreenWidth(window.innerWidth)
  window.addEventListener('resize', handleResize)
  return () => window.removeEventListener('resize', handleResize)
}, [])

const isTablet = screenWidth >= 768 && screenWidth < 1250
const isMobile = screenWidth < 768

  useEffect(() => {
    const t = setTimeout(() => setAnimateBars(true), 140)
    return () => clearTimeout(t)
  }, [])

  useEffect(() => {
    api.get('/api/tracker/dashboard')
      .then(res => setDashboard(res.data))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    api.get('/api/roadmap/current')
      .then(res => {
        if (res.status === 200 && res.data) {
          setCurrentRoadmap(res.data)
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    api.get('/api/resume')
      .then(res => {
        const data = res.data
        if (!data) return

        const hasSummary = !!data.masterResume?.[0]?.summary
        const hasSkills = (data.skills?.length || 0) > 0
        const hasExperience = (data.experiences?.length || 0) > 0
        const hasProjects = (data.projects?.length || 0) > 0

        const score = [hasSummary, hasSkills, hasExperience, hasProjects].filter(Boolean).length * 25

        setResumeStrength({
          score,
          hasSummary,
          hasSkills,
          hasExperience,
          hasProjects
        })
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    setRecLoading(true)
    api.get('/api/jobs/discover/cached')
      .then(res => {
        const jobs = res.data || []
        if (jobs.length > 0) {
          setRecommendedJobs(jobs.slice(0, 5))
          setRecLoading(false)
        } else {
          return api.get('/api/jobs/discover/recommended')
            .then(recRes => {
              setRecommendedJobs((recRes.data || []).slice(0, 5))
            })
            .finally(() => setRecLoading(false))
        }
      })
      .catch(() => {
        api.get('/api/jobs/discover/recommended')
          .then(recRes => setRecommendedJobs((recRes.data || []).slice(0, 5)))
          .catch(() => {})
          .finally(() => setRecLoading(false))
      })
  }, [])

  const today = new Date().toLocaleDateString('en-US', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric'
  }).toUpperCase()

  const resumeItems = useMemo(() => {
    if (!resumeStrength) return []

    return [
      { label: 'Summary', done: resumeStrength.hasSummary },
      { label: 'Skills', done: resumeStrength.hasSkills },
      { label: 'Experience', done: resumeStrength.hasExperience },
      { label: 'Projects', done: resumeStrength.hasProjects }
    ]
  }, [resumeStrength])

  const radius = 34
  const circumference = 2 * Math.PI * radius
  const segmentGap = 10
  const segmentLength = circumference / 4 - segmentGap
  const completed = resumeItems.filter(i => i.done).length

  if (loading) return <DashboardSkeleton />

  const apps = dashboard?.applications || []
  const counts = dashboard?.statusCounts || {}
  const topMatches = [...apps]
    .filter(a => a.matchScore)
    .sort((a, b) => b.matchScore - a.matchScore)

  const primaryActionBtn = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    padding: '10px 18px',
    borderRadius: 18,
    background: '#7c3aed',
    color: '#fff',
    border: 'none',
    fontSize: 14,
    fontWeight: 600,
    cursor: 'pointer',
    boxShadow: '0 8px 18px rgba(124, 58, 237, 0.18)'
  }

  return (
    <div
      style={{
        width: '100%',
        maxWidth: '100%',
        margin: '0 auto',
        overflow: 'hidden',
        paddingBottom: isMobile ? 88 : 0
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: isMobile ? 'stretch' : 'center',
          justifyContent: 'space-between',
          flexDirection: isMobile ? 'column' : 'row',
          gap: isMobile ? 14 : 18,
          marginBottom: isMobile ? 20 : 32,
          width: '100%',
          minWidth: 0
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              width: 46,
              height: 4,
              borderRadius: 999,
              background: '#8b5cf6',
              marginBottom: isMobile ? 12 : 18
            }}
          />

          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: isMobile ? 40 : 46,
                height: isMobile ? 40 : 46,
                borderRadius: 15,
                background: 'rgba(124,58,237,0.10)',
                color: '#7c3aed',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
              }}
            >
              <i className="ti ti-layout-dashboard" style={{ fontSize: isMobile ? 20 : 22 }} />
            </div>

            <div style={{ minWidth: 0 }}>
              <h1
                style={{
                  fontSize: isMobile ? 22 : 32,
                  fontWeight: 800,
                  color: '#1a1040',
                  margin: 0,
                  lineHeight: 1.15
                }}
              >
                Hi {user?.fullName?.trim()?.split(' ')[0] || 'User'} 👋
              </h1>

              <p
                style={{
                  fontSize: isMobile ? 13 : 15,
                  color: '#7c6faa',
                  marginTop: 4,
                  marginBottom: 0
                }}
              >
                Here's your job search at a glance
              </p>
            </div>
          </div>
        </div>

        <div
          style={{
            display: 'flex',
            flexDirection: isMobile ? 'row' : 'column',
            alignItems: isMobile ? 'center' : 'flex-end',
            justifyContent: isMobile ? 'space-between' : 'flex-start',
            width: isMobile ? '100%' : 'auto',
            gap: 10
          }}
        >
          <div
            style={{
              height: isMobile ? 38 : 42,
              padding: isMobile ? '0 12px' : '0 18px',
              borderRadius: 999,
              background: 'rgba(255,255,255,0.72)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#5b21b6',
              fontSize: 12,
              fontWeight: 700,
              whiteSpace: 'nowrap'
            }}
          >
            {today}
          </div>

          <button
            onClick={() => setShowScan(true)}
            style={{
              height: isMobile ? 38 : 42,
              padding: isMobile ? '0 14px' : '0 18px',
              borderRadius: 999,
              border: 'none',
              background: 'linear-gradient(135deg, #7c3aed 0%, #8b5cf6 100%)',
              color: '#fff',
              fontSize: 13,
              fontWeight: 700,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <i className="ti ti-bolt" style={{ fontSize: 14, marginRight: 6 }} />
            Quick JD Scan
          </button>
        </div>

        {showScan && <QuickScanModal onClose={() => setShowScan(false)} />}
      </div>

      {/* Active Roadmap Banner or Cold-Start Launchpad */}
      {currentRoadmap ? (
        <Card style={{ padding: '18px 22px', borderRadius: 20, marginBottom: 20, background: 'linear-gradient(135deg, #ffffff 0%, #faf5ff 100%)', border: '1px solid rgba(124, 58, 237, 0.18)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ width: 44, height: 44, borderRadius: 14, background: 'rgba(124, 58, 237, 0.12)', color: '#7c3aed', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22, flexShrink: 0 }}>
                <i className="ti ti-map-2" />
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 16, fontWeight: 800, color: '#1a1040' }}>
                    Active Prep Roadmap: {currentRoadmap.targetRole}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 700, color: '#10b981', background: 'rgba(16, 185, 129, 0.1)', padding: '2px 8px', borderRadius: 999 }}>
                    {currentRoadmap.targetCompensation || 'Top of Market'}
                  </span>
                </div>
                <div style={{ fontSize: 13, color: '#7c6faa', marginTop: 3 }}>
                  Readiness Score: <b style={{ color: '#7c3aed' }}>{currentRoadmap.overallScore}%</b> • Prep Completion: <b>{currentRoadmap.overallProgress}%</b>
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ width: 130, display: isMobile ? 'none' : 'block' }}>
                <div style={{ height: 8, borderRadius: 999, background: '#e2e8f0', overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${currentRoadmap.overallProgress}%`, background: 'linear-gradient(90deg, #8b5cf6, #7c3aed)', borderRadius: 999, transition: 'width 0.3s ease' }} />
                </div>
              </div>
              <button
                onClick={() => navigate('/roadmap')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '9px 18px',
                  borderRadius: 14,
                  background: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)',
                  color: '#fff',
                  border: 'none',
                  fontSize: 13,
                  fontWeight: 700,
                  cursor: 'pointer',
                  boxShadow: '0 6px 14px rgba(124, 58, 237, 0.2)'
                }}
              >
                Open Roadmap →
              </button>
            </div>
          </div>
        </Card>
      ) : apps.length === 0 ? (
        <Card style={{ padding: '24px 22px', borderRadius: 20, marginBottom: 20, background: 'linear-gradient(135deg, #faf5ff 0%, #f5f3ff 100%)', border: '1px solid rgba(124, 58, 237, 0.2)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16, marginBottom: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ width: 40, height: 40, borderRadius: 12, background: '#7c3aed', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, flexShrink: 0 }}>
                <i className="ti ti-rocket" />
              </div>
              <div>
                <h3 style={{ fontSize: 17, fontWeight: 800, color: '#1a1040', margin: 0 }}>
                  Welcome to Your Career Launchpad 🚀
                </h3>
                <p style={{ fontSize: 13, color: '#7c6faa', margin: 0 }}>
                  Take 3 quick steps to accelerate your path to your target tech role.
                </p>
              </div>
            </div>
            <button
              onClick={() => navigate('/roadmap')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '10px 18px',
                borderRadius: 14,
                background: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)',
                color: '#fff',
                border: 'none',
                fontSize: 13,
                fontWeight: 700,
                cursor: 'pointer',
                boxShadow: '0 6px 14px rgba(124, 58, 237, 0.25)'
              }}
            >
              Start Guided Onboarding
              <i className="ti ti-arrow-right" />
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <div onClick={() => navigate('/roadmap')} style={{ padding: 14, borderRadius: 14, background: '#fff', border: '1px solid rgba(124, 58, 237, 0.12)', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ width: 22, height: 22, borderRadius: '50%', background: '#7c3aed', color: '#fff', fontSize: 11, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>1</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: '#1a1040' }}>Build Prep Roadmap</span>
              </div>
              <div style={{ fontSize: 12, color: '#64748b' }}>Diagnose exact skill gaps for your dream package.</div>
            </div>

            <div onClick={() => navigate('/resume')} style={{ padding: 14, borderRadius: 14, background: '#fff', border: '1px solid rgba(124, 58, 237, 0.12)', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ width: 22, height: 22, borderRadius: '50%', background: '#8b5cf6', color: '#fff', fontSize: 11, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>2</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: '#1a1040' }}>Tailor Resume (X-Y-Z)</span>
              </div>
              <div style={{ fontSize: 12, color: '#64748b' }}>Generate high-impact ATS bullet points.</div>
            </div>

            <div onClick={() => navigate('/discover')} style={{ padding: 14, borderRadius: 14, background: '#fff', border: '1px solid rgba(124, 58, 237, 0.12)', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ width: 22, height: 22, borderRadius: '50%', background: '#a855f7', color: '#fff', fontSize: 11, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>3</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: '#1a1040' }}>Match Compatible Jobs</span>
              </div>
              <div style={{ fontSize: 12, color: '#64748b' }}>Explore verified jobs matching your roadmap.</div>
            </div>
          </div>
        </Card>
      ) : null}

      <div style={{
  display: 'grid',
  gridTemplateColumns: isMobile
    ? '1fr'
    : isTablet
    ? '1fr'
    : '1fr 1fr 0.9fr',
  gap: 16,
  alignItems: 'start',
  marginBottom: 16,
  width: '100%',
  minWidth: 0
}}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Card>
  <div style={{
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 18
  }}>
    <div>
      <div style={{ fontSize: 16, fontWeight: 700, color: '#1a1040' }}>
        Application pipeline
      </div>
      <div style={{ fontSize: 12, color: '#a094c4', marginTop: 3 }}>
        Current status distribution
      </div>
    </div>

    <div style={{
      fontSize: 12,
      fontWeight: 700,
      color: '#7c3aed',
      background: 'rgba(124,58,237,0.08)',
      padding: '6px 10px',
      borderRadius: 999
    }}>
      {Object.values(counts).reduce((a, b) => a + b, 0)} total
    </div>
  </div>

  <DonutChart statusCounts={counts} isMobile={isMobile} />
</Card>
          <Card style={{ minHeight: isMobile ? 'auto' : 310 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18 }}>
              <div style={{ fontSize: 16, fontWeight: 600, color: '#1a1040' }}>
                Recent applications
              </div>
              <span onClick={() => navigate('/tracker')} style={{ fontSize: 13, color: '#a094c4', cursor: 'pointer' }}>
                See all →
              </span>
            </div>

            {apps.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '36px 12px' }}>
                <div style={{ width: 44, height: 44, borderRadius: '50%', background: 'rgba(124,58,237,0.08)', color: '#7c3aed', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 10px', fontSize: 20 }}>
                  <i className="ti ti-briefcase" />
                </div>
                <div style={{ fontSize: 14, fontWeight: 700, color: '#1a1040', marginBottom: 4 }}>
                  No tracked applications yet
                </div>
                <div style={{ fontSize: 12, color: '#7c6faa', marginBottom: 14 }}>
                  Discover verified roles tailored to your target roadmap!
                </div>
                <button onClick={() => navigate('/discover')} style={{ ...primaryActionBtn, margin: '0 auto', fontSize: 13, padding: '8px 16px' }}>
                  <i className="ti ti-compass" />
                  Discover Jobs
                </button>
              </div>
            ) : (
              apps.slice(0, 4).map(app => (
                <div key={app.id} onClick={() => navigate(`/jobs/${app.jobId}`)} style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 10,
                  padding: '12px 0',
                  borderBottom: '0.5px solid #f0eeff',
                  cursor: 'pointer'
                }}>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{
                      fontSize: 14,
                      fontWeight: 600,
                      color: '#1a1040',
                      lineHeight: 1,
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden'
                    }}>
                      {app.jobTitle}
                    </div>

                    <div style={{
                      fontSize: 13,
                      color: '#7c6faa',
                      marginTop: 2,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis'
                    }}>
                      {app.companyName}
                    </div>
                  </div>

                  <StatusBadge status={app.status} />
                </div>
              ))
            )}
          </Card>

        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>


<Card style={{ minHeight: isMobile ? 'auto' : 330 }}>
  <div
    style={{
      fontSize: 16,
      fontWeight: 600,
      color: '#1a1040',
      marginBottom: 18
    }}
  >
    At a glance
  </div>

  <div
    style={{
      display: 'grid',
      gridTemplateColumns: '1fr 1fr',
      gap: isMobile ? 10 : 14
    }}
  >
    {[
      {
        label: 'Total jobs saved',
        value: Object.values(counts).reduce((a, b) => a + b, 0),
        icon: 'ti-briefcase',
        color: '#8b5cf6',
        bg: 'rgba(139,92,246,0.10)'
      },
      {
        label: 'Active pipeline',
        value:
          (counts.APPLIED || 0) +
          (counts.CONTACTED || 0) +
          (counts.INTERVIEW || 0),
        icon: 'ti-activity',
        color: '#3b82f6',
        bg: 'rgba(59,130,246,0.10)'
      },
      {
        label: 'Interviews',
        value: counts.INTERVIEW || 0,
        icon: 'ti-microphone',
        color: '#6366f1',
        bg: 'rgba(99,102,241,0.10)'
      },
      {
        label: 'Offers',
        value: counts.OFFER || 0,
        icon: 'ti-trophy',
        color: '#10b981',
        bg: 'rgba(16,185,129,0.10)'
      }
    ].map(({ label, value, icon, color, bg }) => (
      <div
        key={label}
        onMouseEnter={e => {
          e.currentTarget.style.transform = 'translateY(-2px)'
          e.currentTarget.style.boxShadow =
            '0 12px 24px rgba(124,58,237,0.08)'
        }}
        onMouseLeave={e => {
          e.currentTarget.style.transform = 'translateY(0)'
          e.currentTarget.style.boxShadow =
            '0 2px 8px rgba(124,58,237,0.04)'
        }}
        style={{
          background: '#fff',
          border: '1px solid rgba(124,58,237,0.08)',
          borderRadius: isMobile ? 14 : 18,
          padding: isMobile ? '12px' : '16px',
          minHeight: 20,
          transition: 'all 0.18s ease',
          boxShadow: '0 2px 8px rgba(124,58,237,0.04)',
          cursor: 'default',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'flex-start'
        }}
      >
        <div
          style={{
            width: isMobile ? 32 : 17,
            height: isMobile ? 32 : 17,
            borderRadius: 13,
            background: bg,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}
        >
          <i
            className={`ti ${icon}`}
            style={{
              fontSize: isMobile ? 18 : 25,
              color
            }}
          />
        </div>

        <div
          style={{
            fontSize: isMobile ? 20 : 25,
            fontWeight: 800,
            color: '#1a1040',
            lineHeight: 1,
            marginTop: isMobile ? 10 : 14
          }}
        >
          <AnimatedNumber value={value} />
        </div>

        <div
          style={{
            fontSize: isMobile ? 11.5 : 12.5,
            color: '#7c6faa',
            marginTop: isMobile ? 6 : 8,
            lineHeight: 1.35
          }}
        >
          {label}
        </div>
      </div>
    ))}
  </div>
</Card>

<Card>
  <div style={{
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 18
  }}>
    <div>
      <div style={{ fontSize: 16, fontWeight: 600, color: '#1a1040' }}>
        Resume match scores
      </div>
      <div style={{ fontSize: 12, color: '#a094c4', marginTop: 3 }}>
        How well your resume matches saved jobs
      </div>
    </div>
  </div>

  <div className="smart-scroll" style={{
    maxHeight: isMobile ? 180 : 120,
    overflowY: 'auto',
    paddingRight: 4
  }}>
    {apps.length === 0 ? (
      <div style={{
        padding: '18px 0',
        textAlign: 'center',
        color: '#a094c4',
        fontSize: 13
      }}>
        No match scores yet
      </div>
    ) : (
      apps.map(app => (
        <div key={app.id} style={{
          display: 'grid',
          gridTemplateColumns: isMobile ? '76px 1fr 34px' : '110px 1fr 38px',
          alignItems: 'center',
          gap: isMobile ? 8 : 12,
          padding: '10px 0',
          borderBottom: '0.5px solid #f0eeff'
        }}>
          <div style={{
            fontSize: 13,
            fontWeight: 600,
            color: '#1a1040',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis'
          }}>
            {app.companyName}
          </div>

          <div style={{
            height: 7,
            background: '#ede9fe',
            borderRadius: 999,
            overflow: 'hidden'
          }}>
            <div style={{
              height: '100%',
              borderRadius: 999,
              background: 'linear-gradient(90deg, #7c3aed, #a78bfa)',
              width: animateBars ? `${app.matchScore || 0}%` : '0%',
              transition: 'width 0.8s ease'
            }} />
          </div>

          <div style={{
            fontSize: 12,
            fontWeight: 700,
            color: '#5b21b6',
            textAlign: 'right'
          }}>
            <AnimatedNumber value={app.matchScore || 0} suffix="%" />
          </div>
        </div>
      ))
    )}
  </div>
</Card>
      </div>

<Card style={{ height: isMobile ? 'auto' : 570, overflow: 'hidden' }}>
  <div style={{
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 18,
    gridColumn: isTablet && !isMobile ? '1 / -1' : 'auto',
  }}>
    <div>
      <div style={{ fontSize: 16, fontWeight: 700, color: '#1a1040' }}>
        ✨ Recommended
      </div>
      <div style={{ fontSize: 12, color: '#a094c4', marginTop: 3 }}>
        Jobs matching your profile
      </div>
    </div>

    <span onClick={() => navigate('/discover')} style={{
      fontSize: 12,
      color: '#7c3aed',
      cursor: 'pointer',
      fontWeight: 600
    }}>
      See all →
    </span>
  </div>

  <div className="smart-scroll" style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
maxHeight: isMobile ? 'none' : 410,
overflowY: isMobile ? 'visible' : 'auto',
    paddingRight: 4
  }}>
    {recLoading ? (
      [1, 2, 3].map(n => (
        <div
          key={n}
          style={{
            padding: '13px 14px',
            borderRadius: 16,
            background: '#faf8ff',
            border: '1px solid rgba(124,58,237,0.08)',
            display: 'flex',
            alignItems: 'center',
            gap: 11,
            opacity: 0.75
          }}
        >
          <div style={{
            width: 38,
            height: 38,
            borderRadius: 13,
            background: 'rgba(124,58,237,0.12)',
            flexShrink: 0
          }} />
          <div style={{ flex: 1 }}>
            <div style={{
              width: '70%',
              height: 14,
              borderRadius: 6,
              background: 'rgba(124,58,237,0.14)',
              marginBottom: 8
            }} />
            <div style={{
              width: '45%',
              height: 10,
              borderRadius: 4,
              background: 'rgba(124,58,237,0.08)'
            }} />
          </div>
        </div>
      ))
    ) : recommendedJobs.length === 0 ? (
      <div style={{
        padding: '28px 16px',
        textAlign: 'center',
        background: '#faf8ff',
        borderRadius: 16,
        border: '1px dashed rgba(124,58,237,0.22)'
      }}>
        <div style={{ fontSize: 24, marginBottom: 8 }}>💼</div>
        <div style={{ fontSize: 13.5, fontWeight: 700, color: '#1a1040', marginBottom: 4 }}>
          Discovering matches for your profile...
        </div>
        <div style={{ fontSize: 12, color: '#7c3aed', marginBottom: 14 }}>
          Explore hundreds of active tech openings curated from top career portals.
        </div>
        <button
          type="button"
          onClick={() => navigate('/discover')}
          style={{
            padding: '7px 16px',
            borderRadius: 10,
            background: '#7c3aed',
            color: '#fff',
            border: 'none',
            fontSize: 12,
            fontWeight: 700,
            cursor: 'pointer'
          }}
        >
          Explore Openings →
        </button>
      </div>
    ) : (
      recommendedJobs.slice(0, 5).map((job, i) => (
        <div
          key={job.id || i}
          onMouseEnter={e => {
            e.currentTarget.style.transform = 'translateY(-2px)'
            e.currentTarget.style.boxShadow = '0 10px 22px rgba(124,58,237,0.08)'
            e.currentTarget.style.borderColor = 'rgba(124,58,237,0.22)'
          }}
          onMouseLeave={e => {
            e.currentTarget.style.transform = 'translateY(0)'
            e.currentTarget.style.boxShadow = 'none'
            e.currentTarget.style.borderColor = 'rgba(124,58,237,0.10)'
          }}
          style={{
            padding: '13px 14px',
            borderRadius: 16,
            background: '#faf8ff',
            border: '1px solid rgba(124,58,237,0.10)',
            transition: 'all 0.18s ease',
            cursor: 'pointer'
          }}
          onClick={() => handleApplyClick(job)}
        >
          <div style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 11
          }}>
            <div style={{
              width: 38,
              height: 38,
              borderRadius: 13,
              background: 'rgba(124,58,237,0.10)',
              color: '#7c3aed',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 14,
              fontWeight: 800,
              flexShrink: 0
            }}>
              {(job.company || job.companyName || '?').charAt(0).toUpperCase()}
            </div>

            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{
                display: 'flex',
                alignItems: 'flex-start',
                justifyContent: 'space-between',
                gap: 8
              }}>
                <div style={{
                  fontSize: 13.5,
                  fontWeight: 800,
                  color: '#1a1040',
                  lineHeight: 1.35
                }}>
                  {job.title}
                </div>

                {job.matchScore && (
                  <span style={{
                    padding: '2px 7px',
                    borderRadius: 999,
                    fontSize: 11,
                    fontWeight: 700,
                    background: job.matchScore >= 80 ? 'rgba(16,185,129,0.12)' : 'rgba(124,58,237,0.12)',
                    color: job.matchScore >= 80 ? '#059669' : '#7c3aed',
                    whiteSpace: 'nowrap',
                    flexShrink: 0
                  }}>
                    {job.matchScore}% Match
                  </span>
                )}
              </div>

              <div style={{
                fontSize: 12.5,
                color: '#7c3aed',
                marginTop: 4,
                fontWeight: 600
              }}>
                {job.company || job.companyName}
              </div>

              {job.location && (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  fontSize: 11.5,
                  color: '#a094c4',
                  marginTop: 5
                }}>
                  <i className="ti ti-map-pin" style={{ fontSize: 12 }} />
                  <span>{job.location}</span>
                </div>
              )}

              {(job.url || job.applyUrl) && (
                <button
                  type="button"
                  onClick={e => {
                    e.stopPropagation()
                    handleApplyClick(job)
                  }}
                  style={{
                    marginTop: 9,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 5,
                    fontSize: 12,
                    color: '#7c3aed',
                    background: 'none',
                    border: 'none',
                    padding: 0,
                    textDecoration: 'none',
                    fontWeight: 700,
                    cursor: 'pointer'
                  }}
                >
                  <i className="ti ti-external-link" style={{ fontSize: 12 }} />
                  Apply / View job
                </button>
              )}
            </div>
          </div>
        </div>
      ))
    )}
  </div>

  <button onClick={() => navigate('/discover')} style={{
    width: '100%',
    marginTop: 18,
    padding: '9px',
    borderRadius: 12,
    background: '#f5f0ff',
    border: '1px solid rgba(124,58,237,0.18)',
    color: '#7c3aed',
    fontSize: 13,
    fontWeight: 700,
    cursor: 'pointer'
  }}>
    Discover more jobs →
  </button>
</Card>


      </div>

      {(apps.some(a => a.status === 'INTERVIEW') || resumeStrength !== null) && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: (apps.some(a => a.status === 'INTERVIEW') && resumeStrength !== null)
            ? (isMobile ? 'minmax(0, 1fr)' : 'minmax(0, 1fr) minmax(0, 1fr)')
            : 'minmax(0, 1fr)',
          gap: 16,
          marginBottom: 16,
          width: '100%',
          maxWidth: '100%',
          minWidth: 0,
          boxSizing: 'border-box',
          alignItems: 'stretch'
        }}>
          {apps.some(a => a.status === 'INTERVIEW') && (
            <Card style={{ minWidth: 0, maxWidth: '100%', overflow: 'hidden' }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 10,
                marginBottom: 18,
                minWidth: 0,
                width: '100%'
              }}>
                <div style={{
                  fontSize: isMobile ? 14 : 16,
                  fontWeight: 600,
                  color: '#1a1040',
                  lineHeight: 1.25
                }}>
                  🎯 Upcoming Interviews
                </div>
                <span style={{
                  fontSize: 12,
                  background: '#ede9fe',
                  color: '#5b21b6',
                  padding: '3px 10px',
                  borderRadius: 99,
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                  flexShrink: 0
                }}>
                  {apps.filter(a => a.status === 'INTERVIEW').length} scheduled
                </span>
              </div>

              {apps.filter(a => a.status === 'INTERVIEW').map(app => (
                <div key={app.id} style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 10,
                  padding: '10px 12px',
                  borderRadius: 10,
                  background: '#faf8ff',
                  border: '1px solid #ede9fe',
                  marginBottom: 8,
                  minWidth: 0,
                  width: '100%',
                  boxSizing: 'border-box'
                }}>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{
                      fontSize: isMobile ? 13 : 14,
                      fontWeight: 600,
                      color: '#1a1040',
                      lineHeight: 1.3,
                      wordBreak: 'break-word'
                    }}>
                      {app.jobTitle}
                    </div>
                    <div style={{
                      fontSize: 12,
                      color: '#7c6faa',
                      marginTop: 3,
                      lineHeight: 1.3,
                      wordBreak: 'break-word'
                    }}>
                      {app.companyName}
                    </div>
                  </div>

                  <button onClick={() => navigate(`/jobs/${app.jobId}/prep`)} style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 5,
                    padding: '6px 12px',
                    borderRadius: 8,
                    background: '#7c3aed',
                    color: '#fff',
                    border: 'none',
                    fontSize: 12,
                    fontWeight: 500,
                    cursor: 'pointer',
                    whiteSpace: 'nowrap',
                    flexShrink: 0
                  }}>
                    <i className="ti ti-brain" style={{ fontSize: 13 }} />
                    Prep
                  </button>
                </div>
              ))}
            </Card>
          )}

          {resumeStrength !== null && (
            <Card style={{ minWidth: 0, maxWidth: '100%', overflow: 'hidden' }}>
              <div style={{
                fontSize: 16,
                fontWeight: 600,
                color: '#1a1040',
                marginBottom: 6
              }}>
                📄 Resume Strength
              </div>

              <p style={{
                fontSize: 13,
                color: '#7c6faa',
                marginBottom: 18,
                lineHeight: 1.4
              }}>
                Keep your resume complete for better AI tailoring
              </p>

              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: isMobile ? 16 : 28,
                marginTop: isMobile ? 12 : 22,
                minWidth: 0,
                width: '100%'
              }}>
                <div style={{
                  position: 'relative',
                  width: isMobile ? 78 : 96,
                  height: isMobile ? 78 : 96,
                  flexShrink: 0
                }}>
                  <svg width={isMobile ? 78 : 96} height={isMobile ? 78 : 96} viewBox="0 0 96 96">
                    <defs>
                      <filter id="resumeSoftGlow">
                        <feGaussianBlur stdDeviation="1.4" result="blur" />
                        <feMerge>
                          <feMergeNode in="blur" />
                          <feMergeNode in="SourceGraphic" />
                        </feMerge>
                      </filter>
                    </defs>

                    {[0, 1, 2, 3].map(i => {
                      const dashOffset = -(i * circumference / 4)
                      const isDone = i < completed

                      return (
                        <circle
                          key={i}
                          cx="48"
                          cy="48"
                          r={radius}
                          fill="none"
                          stroke={isDone ? '#8b5cf6' : '#ede9fe'}
                          strokeWidth="10"
                          strokeLinecap="round"
                          strokeDasharray={`${segmentLength} ${circumference}`}
                          strokeDashoffset={dashOffset}
                          transform="rotate(-90 48 48)"
                          filter={isDone ? 'url(#resumeSoftGlow)' : 'none'}
                          style={{
                            opacity: isDone ? 1 : 0.95
                          }}
                        />
                      )
                    })}
                  </svg>

                  <div style={{
                    position: 'absolute',
                    inset: isMobile ? 11 : 14,
                    borderRadius: '50%',
                    background: '#fff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    boxShadow: 'inset 0 0 0 1px #f0eeff'
                  }}>
                    <div style={{
                      fontSize: isMobile ? 15 : 18,
                      fontWeight: 800,
                      color: '#7c3aed'
                    }}>
                      <AnimatedNumber value={resumeStrength.score} suffix="%" />
                    </div>
                  </div>
                </div>

                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: isMobile ? 7 : 9,
                  minWidth: 0,
                  flex: 1
                }}>
                  {resumeItems.map(({ label, done }) => (
                    <div key={label} style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      fontSize: isMobile ? 13 : 14,
                      color: done ? '#1a1040' : '#a094c4',
                      whiteSpace: 'nowrap'
                    }}>
                      <i
                        className={`ti ${done ? 'ti-circle-check-filled' : 'ti-circle'}`}
                        style={{
                          fontSize: isMobile ? 15 : 16,
                          color: done ? '#7c3aed' : '#c4b5fd',
                          flexShrink: 0
                        }}
                      />
                      <span>{label}</span>
                    </div>
                  ))}
                </div>
              </div>

              {resumeStrength.score < 100 && (
                <button onClick={() => navigate('/resume')} style={{
                  width: '100%',
                  marginTop: 20,
                  padding: 8,
                  borderRadius: 8,
                  background: 'transparent',
                  border: '1px solid #c4b5fd',
                  color: '#7c3aed',
                  fontSize: 13,
                  fontWeight: 500,
                  cursor: 'pointer',
                  fontFamily: 'inherit'
                }}>
                  Complete your resume →
                </button>
              )}
            </Card>
          )}
        </div>
      )}

{topMatches.length > 0 && (
  <Card>
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      marginBottom: 18
    }}>
      <div>
        <div style={{
          fontSize: 16,
          fontWeight: 700,
          color: '#1a1040'
        }}>
          💎 Top matches for you
        </div>

        <div style={{
          fontSize: 12,
          color: '#a094c4',
          marginTop: 3
        }}>
          Highest resume compatibility jobs
        </div>
      </div>

      <span
        onClick={() => navigate('/jobs')}
        style={{
          fontSize: 12,
          color: '#7c3aed',
          cursor: 'pointer',
          fontWeight: 600
        }}
      >
        View all →
      </span>
    </div>

    <div
      style={{
        display: 'grid',
        gridTemplateColumns: isMobile ? '1fr' : isTablet ? '1fr 1fr' : '1fr 1fr 1fr',
        gap: 14
      }}
    >
      {topMatches.slice(0, 3).map(app => {
        const score = app.matchScore || 0

        const label =
          score >= 85
            ? 'Excellent fit'
            : score >= 70
            ? 'Strong match'
            : 'Good potential'

        return (
          <div
            key={app.id}

            onClick={() => navigate(`/jobs/${app.jobId}`)}

            onMouseEnter={e => {
              e.currentTarget.style.transform = 'translateY(-3px)'
              e.currentTarget.style.boxShadow =
                '0 14px 28px rgba(124,58,237,0.10)'
              e.currentTarget.style.borderColor =
                'rgba(124,58,237,0.20)'
            }}

            onMouseLeave={e => {
              e.currentTarget.style.transform = 'translateY(0)'
              e.currentTarget.style.boxShadow =
                '0 2px 8px rgba(124,58,237,0.04)'
              e.currentTarget.style.borderColor =
                'rgba(139,92,246,0.10)'
            }}

            style={{
              background: '#fff',

              borderRadius: 18,

              padding: 18,

              border:
                '1px solid rgba(139,92,246,0.10)',

              cursor: 'pointer',

              transition: 'all 0.18s ease',

              boxShadow:
                '0 2px 8px rgba(124,58,237,0.04)',

              position: 'relative',

              overflow: 'hidden'
            }}
          >
            {/* Glow */}
            <div
              style={{
                position: 'absolute',
                top: -40,
                right: -30,

                width: 120,
                height: 120,

                borderRadius: '50%',

                background:
                  'radial-gradient(rgba(167,139,250,0.16), transparent 70%)',

                pointerEvents: 'none'
              }}
            />

            {/* Header */}
            <div style={{
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              gap: 12
            }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                minWidth: 0
              }}>
                <div style={{
                  width: 42,
                  height: 42,

                  borderRadius: 14,

                  background:
                    'linear-gradient(135deg, rgba(124,58,237,0.10), rgba(167,139,250,0.18))',

                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',

                  color: '#7c3aed',

                  fontSize: 16,
                  fontWeight: 800,

                  flexShrink: 0
                }}>
                  {app.companyName?.charAt(0)?.toUpperCase()}
                </div>

                <div style={{ minWidth: 0 }}>
                  <div style={{
                    fontSize: 14,
                    fontWeight: 700,
                    color: '#1a1040',

                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis'
                  }}>
                    {app.companyName}
                  </div>

                  <div style={{
                    fontSize: 12,
                    color: '#7c6faa',
                    marginTop: 2,

                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis'
                  }}>
                    {app.jobTitle}
                  </div>
                </div>
              </div>

              {/* Score ring */}
              <div
                style={{
                  position: 'relative',
                  width: 58,
                  height: 58,
                  flexShrink: 0
                }}
              >
                <svg width="58" height="58">
                  <circle
                    cx="29"
                    cy="29"
                    r="24"
                    stroke="#ede9fe"
                    strokeWidth="5"
                    fill="none"
                  />

                  <circle
                    cx="29"
                    cy="29"
                    r="24"
                    stroke="#7c3aed"
                    strokeWidth="5"
                    fill="none"
                    strokeLinecap="round"
                    strokeDasharray={150.8}
                    strokeDashoffset={
                      150.8 - (score / 100) * 150.8
                    }
                    transform="rotate(-90 29 29)"
                  />
                </svg>

                <div style={{
                  position: 'absolute',
                  inset: 0,

                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',

                  fontSize: 12,
                  fontWeight: 800,
                  color: '#7c3aed'
                }}>
                  {score}%
                </div>
              </div>
            </div>

            {/* Match label */}
            <div
              style={{
                marginTop: 16,

                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,

                padding: '6px 10px',

                borderRadius: 999,

                background:
                  'rgba(124,58,237,0.08)',

                color: '#6d28d9',

                fontSize: 11,
                fontWeight: 700
              }}
            >
              <i
                className="ti ti-sparkles"
                style={{ fontSize: 12 }}
              />

              {label}
            </div>

            {/* Footer */}
            <div style={{
              marginTop: 18,

              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}>
              <button
                onClick={e => {
                  e.stopPropagation()
                  navigate(`/jobs/${app.jobId}`)
                }}

                style={{
                  height: 34,

                  padding: '0 14px',

                  borderRadius: 10,

                  border: 'none',

                  background: '#7c3aed',
                  color: '#fff',

                  fontSize: 12,
                  fontWeight: 700,

                  cursor: 'pointer'
                }}
              >
                Open job
              </button>

              <button
                onClick={e => {
                  e.stopPropagation()
                  navigate(`/jobs/${app.jobId}/prep`)
                }}

                style={{
                  height: 34,

                  padding: '0 14px',

                  borderRadius: 10,

                  border:
                    '1px solid rgba(124,58,237,0.16)',

                  background: '#fff',

                  color: '#7c3aed',

                  fontSize: 12,
                  fontWeight: 700,

                  cursor: 'pointer'
                }}
              >
                Prep
              </button>
            </div>
          </div>
        )
      })}
    </div>
  </Card>
)}

      <ApplyConfirmModal
        job={applyingJob}
        isOpen={!!applyingJob}
        onClose={() => setApplyingJob(null)}
        onTrackSuccess={handleTrackSuccess}
      />
    </div>
  )
}