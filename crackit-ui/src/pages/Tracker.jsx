import { useEffect, useState } from 'react'
import api from '../api/axios'
import Card from '../components/ui/Card'
import StatusBadge from '../components/ui/StatusBadge'
import { STATUS_ORDER } from '../styles/theme'
import { TrackerSkeleton } from '../components/Skeletons'
import PageHeader from '../components/ui/PageHeader'
import { useToast } from '../components/ui/ToastProvider'

export default function Tracker() {
  const [applications, setApplications] = useState([])
  const [selected, setSelected] = useState(null)
  const [form, setForm] = useState({ status: '', notes: '', contactPerson: '', appliedDate: '' })
  const [loading, setLoading] = useState(false)
  const [pageLoading, setPageLoading] = useState(true)
  const [screenWidth, setScreenWidth] = useState(window.innerWidth)
  const { showToast } = useToast()

  useEffect(() => {
    api.get('/api/tracker')
      .then(res => setApplications(res.data))
      .finally(() => setPageLoading(false))

    // Check if user was redirected from email 1-click tracking
    const params = new URLSearchParams(window.location.search)
    if (params.get('tracked') === 'applied') {
      showToast({
        type: 'success',
        message: 'Application successfully added to your pipeline from email! 🎉'
      })
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [])

  const isTablet = screenWidth < 1250
const isMobile = screenWidth < 650

  const openUpdate = (app) => {
    setSelected(app)
    setForm({ status: app.status, notes: app.notes || '', contactPerson: app.contactPerson || '', appliedDate: app.appliedDate || '' })
  }

  const handleUpdate = async (e) => {
    e.preventDefault()
    setLoading(true)
    try {
      const res = await api.patch(`/api/tracker/${selected.id}/status`, form)
      setApplications(prev => prev.map(a => a.id === selected.id ? res.data : a))
      setSelected(null)
    } catch { alert('Update failed') }
    finally { setLoading(false) }
  }

  const [statusFilter, setStatusFilter] = useState('ALL')

const filteredApplications =
  statusFilter === 'ALL'
    ? applications
    : applications.filter(app => app.status === statusFilter)

  if (pageLoading) return <TrackerSkeleton />

  return (
    <div
  style={{
    width: '100%',
    maxWidth: 1200
  }}
>
      <PageHeader
  title="Tracker"
  subtitle="Manage your application statuses"
  icon="ti-chart-arrows-vertical"
/>
 {/* Pipeline summary */}
<div
  className="app-card"
  style={{
    padding: '18px 22px',
    marginBottom: 18,
    borderRadius: 20
  }}
>
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      marginBottom: 16
    }}
  >
    <div>
      <div
        style={{
          fontSize: 15,
          fontWeight: 700,
          color: '#1a1040'
        }}
      >
        Pipeline overview
      </div>

      <div
        style={{
          fontSize: 12,
          color: '#8b7bb4',
          marginTop: 3
        }}
      >
        Track your application progress
      </div>
    </div>

    <div
      style={{
        fontSize: 13,
        fontWeight: 700,
        color: '#7c3aed',
        background: 'rgba(124,58,237,0.08)',
        padding: '8px 12px',
        borderRadius: 999
      }}
    >
      {applications.length} total
    </div>
  </div>

<div
  style={{
    display: 'grid',
    gridTemplateColumns: isMobile
  ? '1fr 1fr 1fr'
  : isTablet
  ? 'repeat(3, 1fr)'
  : 'repeat(6, 1fr)',
    gap: 8
  }}
>
  {STATUS_ORDER.map(status => {
    const count = applications.filter(
      a => a.status === status
    ).length

    const active = statusFilter === status

    return (
  <div
  key={status}

  onClick={() =>
    setStatusFilter(
      active ? 'ALL' : status
    )
  }

  onMouseEnter={e => {
    if (!active) {
      e.currentTarget.style.transform = 'translateY(-2px)'
      e.currentTarget.style.borderColor = 'rgba(124,58,237,0.22)'
    }
  }}

  onMouseLeave={e => {
    if (!active) {
      e.currentTarget.style.transform = 'translateY(0)'
      e.currentTarget.style.borderColor = '#eee7ff'
    }
  }}

  style={{
    background: active
      ? 'linear-gradient(135deg, rgba(124,58,237,0.12), rgba(167,139,250,0.18))'
      : '#faf8ff',

    border: active
      ? '1px solid rgba(124,58,237,0.35)'
      : '1px solid #eee7ff',

    borderRadius: 14,
    padding: '12px 10px',
    minWidth: 0,

    cursor: 'pointer',

    transition: 'all 0.18s ease',

    transform: active
      ? 'translateY(-1px)'
      : 'translateY(0)',

    boxShadow: active
      ? '0 6px 18px rgba(124,58,237,0.12)'
      : 'none'
  }}
>          <div
            style={{
              fontSize: 10,
              fontWeight: 700,
              color: active ? '#6d28d9' : '#8b7bb4',
              marginBottom: 8
            }}
          >
            {status}
          </div>

          <div
            style={{
              fontSize: 24,
              fontWeight: 800,
              color: active ? '#6d28d9' : '#1a1040'
            }}
          >
            {count}
          </div>
          <div
  style={{
    marginTop: 4,
    fontSize: 10,
    fontWeight: 600,
    color: active ? '#7c3aed' : '#b09adf'
  }}
>
  {active ? 'Showing' : 'Filter'}
</div>
        </div>
      )
    })}
  </div>
</div>

{/* Application cards */}
<div
  style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 14
  }}
>
  {applications.length === 0 && (
    <div
      className="app-card"
      style={{
        padding: 28,
        textAlign: 'center'
      }}
    >
      <i
        className="ti ti-briefcase"
        style={{
          fontSize: 34,
          color: '#a78bfa'
        }}
      />

      <div
        style={{
          marginTop: 10,
          fontSize: 15,
          fontWeight: 600,
          color: '#1a1040'
        }}
      >
        No applications yet
      </div>

      <div
        style={{
          marginTop: 4,
          fontSize: 13,
          color: '#8b7bb4'
        }}
      >
        Start applying to jobs and track them here.
      </div>
    </div>
  )}

  {filteredApplications.map(app => (
    <Card
  key={app.id}
  style={{
    padding: '18px 20px',
    borderRadius: 20,
    display: 'flex',
    flexDirection: isMobile ? 'column' : 'row',
    alignItems: isMobile ? 'stretch' : 'center',
    justifyContent: 'space-between',
    gap: isMobile ? 14 : 18,
    minHeight: isMobile ? 'auto' : 96
  }}
>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          minWidth: 0
        }}
      >
        <div
          style={{
            width: 52,
            height: 52,
            borderRadius: 16,
            background:
              'linear-gradient(135deg, rgba(124,58,237,0.12), rgba(167,139,250,0.18))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}
        >
          <span
            style={{
              fontSize: 18,
              fontWeight: 800,
              color: '#7c3aed'
            }}
          >
            {app.companyName?.charAt(0)?.toUpperCase() || 'C'}
          </span>
        </div>

        <div style={{ minWidth: 0 }}>
          <div
            style={{
              fontSize: 16,
              fontWeight: 700,
              color: '#1a1040',
              marginBottom: 4
            }}
          >
            {app.jobTitle}
          </div>

          <div
            style={{
              fontSize: 13,
              color: '#7c6faa',
              marginBottom: 8
            }}
          >
            {app.companyName}
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              flexWrap: 'wrap'
            }}
          >
            {app.contactPerson && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 5,
                  fontSize: 12,
                  color: '#8b7bb4'
                }}
              >
                <i className="ti ti-user" />
                {app.contactPerson}
              </div>
            )}

            {app.appliedDate && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 5,
                  fontSize: 12,
                  color: '#8b7bb4'
                }}
              >
                <i className="ti ti-calendar" />
                {app.appliedDate}
              </div>
            )}
          </div>
        </div>
      </div>
<div
  style={{
    display: 'flex',
    alignItems: 'center',
    justifyContent: isMobile ? 'flex-start' : 'flex-end',
    gap: 12,
    flexShrink: 0,
    marginLeft: isMobile ? 68 : 0
  }}
>
        <StatusBadge status={app.status} />

        <button
          onClick={() => openUpdate(app)}
          style={outlineBtnStyle}
        >
          Update
        </button>
      </div>
    </Card>
  ))}
</div>


      {/* Modal */}
{selected && (
  <div
    style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(26,16,64,0.45)',
      backdropFilter: 'blur(6px)',
      display: 'flex',
      alignItems: isMobile ? 'flex-start' : 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: isMobile ? '14px 14px 130px' : 20,
      overflowY: 'auto',
      marginBottom: isMobile ? 24 : 0,
    }}
  >
    <div
      style={{
        background: '#fff',
        borderRadius: isMobile ? 22 : 20,
        width: '100%',
        maxWidth: 430,
        maxHeight: isMobile ? 'calc(100dvh - 28px)' : 'none',
        border: '1px solid rgba(139,92,246,0.15)',
        boxShadow: '0 28px 80px rgba(26,16,64,0.28)',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column'
      }}
    >
      <div
        style={{
          padding: '22px 24px 16px',
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 14,
          borderBottom: '1px solid rgba(139,92,246,0.08)'
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div
            style={{
              fontSize: 17,
              fontWeight: 800,
              color: '#1a1040',
              lineHeight: 1.3
            }}
          >
            Update Status
          </div>

          <div
            style={{
              marginTop: 4,
              fontSize: 13,
              color: '#7c6faa',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              maxWidth: 300
            }}
          >
            {selected.jobTitle}
          </div>
        </div>

        <button
          onClick={() => setSelected(null)}
          style={{
            width: 36,
            height: 36,
            borderRadius: 12,
            background: '#f5f0ff',
            border: 'none',
            color: '#7c3aed',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}
        >
          <i className="ti ti-x" aria-hidden="true" />
        </button>
      </div>

      <form
        onSubmit={handleUpdate}
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: 0
        }}
      >
        <div
          style={{
            padding: '18px 24px',
            overflowY: 'auto',
            maxHeight: isMobile ? 'calc(100dvh - 190px)' : 'none'
          }}
        >
          <div style={{ marginBottom: 14 }}>
            <label style={labelStyle}>Status</label>
            <select
              value={form.status}
              onChange={e => setForm({ ...form, status: e.target.value })}
              style={inputStyle}
            >
              {STATUS_ORDER.map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          <div style={{ marginBottom: 14 }}>
            <label style={labelStyle}>Contact Person</label>
            <input
              value={form.contactPerson}
              onChange={e => setForm({ ...form, contactPerson: e.target.value })}
              placeholder="HR name..."
              style={inputStyle}
            />
          </div>

          <div style={{ marginBottom: 14 }}>
            <label style={labelStyle}>Applied Date</label>
            <input
              type="date"
              value={form.appliedDate}
              onChange={e => setForm({ ...form, appliedDate: e.target.value })}
              style={inputStyle}
            />
          </div>

          <div>
            <label style={labelStyle}>Notes</label>
            <textarea
              value={form.notes}
              onChange={e => setForm({ ...form, notes: e.target.value })}
              placeholder="Any notes..."
              style={{
                ...inputStyle,
                minHeight: 95,
                resize: 'vertical',
                lineHeight: 1.5
              }}
            />
          </div>
        </div>

        <div
          style={{
            padding: '14px 24px 20px',
            borderTop: '1px solid rgba(139,92,246,0.10)',
            background: '#fff',
            position: 'sticky',
            bottom: 0
          }}
        >
          <button
            type="submit"
            disabled={loading}
            style={{
              ...btnStyle,
              width: '100%',
              justifyContent: 'center',
              opacity: loading ? 0.65 : 1,
              height: 42,
              fontWeight: 800
            }}
          >
            {loading ? 'Updating...' : 'Update Status'}
          </button>
        </div>
      </form>
    </div>
  </div>
)}
    </div>
  )
}

const inputStyle = {
  width: '100%', padding: '9px 12px', borderRadius: 10,
  border: '1px solid #e4daff', fontSize: 13, outline: 'none',
  color: '#1a1040', background: '#faf8ff'
}
const labelStyle = { fontSize: 11, color: '#7c6faa', display: 'block', marginBottom: 5 }
const btnStyle = {
  display: 'flex', alignItems: 'center', gap: 6,
  padding: '9px 18px', borderRadius: 10,
  background: '#7c3aed', color: '#fff', border: 'none',
  fontSize: 13, fontWeight: 500, cursor: 'pointer'
}
const outlineBtnStyle = {
  padding: '8px 16px',
  borderRadius: 12,
  background: '#fff',
  color: '#7c3aed',
  border: '1px solid #e4daff',
  fontSize: 12,
  fontWeight: 700,
  cursor: 'pointer',
  transition: 'all 0.18s ease'
}