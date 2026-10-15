import { useState } from 'react'
import { createPortal } from 'react-dom'
import api from '../../api/axios'

export default function ApplyConfirmModal({ job, isOpen, onClose, onTrackSuccess }) {
  const [loading, setLoading] = useState(false)
  const [notes, setNotes] = useState('')
  const [showNotes, setShowNotes] = useState(false)

  if (!isOpen || !job) return null

  const company = job.company || job.companyName || 'Hiring Company'
  const title = job.title || 'Software Engineer'
  const location = job.location || ''
  const applyUrl = job.url || job.applyUrl || ''

  const handleConfirmApplied = async () => {
    setLoading(true)
    try {
      const payload = {
        jobId: job.id,
        companyName: company,
        title: title,
        location: location,
        applyUrl: applyUrl,
        jdText: job.description || job.jdText || '',
        source: job.source || (job.sourceType ? job.sourceType : 'IN_APP_APPLY'),
        notes: notes.trim() || undefined
      }

      const res = await api.post('/api/tracker/track-apply', payload)
      if (onTrackSuccess) {
        onTrackSuccess(res.data)
      }
      onClose()
    } catch (err) {
      console.error('Failed to track application:', err)
      // If error occurs, still close or let user retry
    } finally {
      setLoading(false)
    }
  }

  return createPortal(
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
        backdropFilter: 'blur(6px)',
        WebkitBackdropFilter: 'blur(6px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 99999,
        padding: 16,
        animation: 'crackitFadeIn 0.2s ease-out'
      }}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          background: '#ffffff',
          borderRadius: 22,
          maxWidth: 460,
          width: '100%',
          padding: '26px 24px',
          boxShadow: '0 25px 60px -15px rgba(124, 58, 237, 0.25), 0 0 0 1px rgba(124, 58, 237, 0.12)',
          textAlign: 'center',
          position: 'relative'
        }}
      >
        {/* Close icon button */}
        <button
          onClick={onClose}
          style={{
            position: 'absolute',
            top: 16,
            right: 16,
            background: 'transparent',
            border: 'none',
            fontSize: 18,
            color: '#94a3b8',
            cursor: 'pointer',
            padding: 4,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: '50%'
          }}
          title="Close"
        >
          ✕
        </button>

        {/* Header Icon */}
        <div
          style={{
            width: 58,
            height: 58,
            borderRadius: 18,
            background: 'linear-gradient(135deg, #ede9fe 0%, #f3e8ff 100%)',
            border: '1px solid #ddd6fe',
            color: '#7c3aed',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 26,
            marginBottom: 14
          }}
        >
          🎯
        </div>

        <h3
          style={{
            margin: '0 0 6px 0',
            fontSize: 20,
            fontWeight: 800,
            color: '#1a1040',
            letterSpacing: '-0.3px'
          }}
        >
          Did you apply?
        </h3>

        <p
          style={{
            margin: '0 0 18px 0',
            fontSize: 13.5,
            color: '#64748b',
            lineHeight: 1.5
          }}
        >
          We opened the job portal in a new tab. If you submitted your application, add it to your pipeline to track your status!
        </p>

        {/* Job Mini Card */}
        <div
          style={{
            background: '#faf8ff',
            border: '1px solid rgba(124, 58, 237, 0.12)',
            borderRadius: 14,
            padding: '12px 14px',
            textAlign: 'left',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 16
          }}
        >
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 12,
              background: 'rgba(124, 58, 237, 0.12)',
              color: '#7c3aed',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 16,
              fontWeight: 800,
              flexShrink: 0
            }}
          >
            {company.charAt(0).toUpperCase()}
          </div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div
              style={{
                fontSize: 14,
                fontWeight: 800,
                color: '#1a1040',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis'
              }}
            >
              {title}
            </div>
            <div
              style={{
                fontSize: 12.5,
                color: '#7c3aed',
                fontWeight: 600,
                marginTop: 2
              }}
            >
              {company} {location ? `• ${location}` : ''}
            </div>
          </div>
        </div>

        {/* Optional Notes toggle */}
        {!showNotes ? (
          <button
            type="button"
            onClick={() => setShowNotes(true)}
            style={{
              background: 'none',
              border: 'none',
              color: '#7c3aed',
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
              marginBottom: 16,
              padding: '2px 0'
            }}
          >
            + Add optional notes / referral
          </button>
        ) : (
          <div style={{ marginBottom: 16, textAlign: 'left' }}>
            <label
              style={{
                display: 'block',
                fontSize: 11.5,
                fontWeight: 700,
                color: '#64748b',
                marginBottom: 4
              }}
            >
              Notes / Referral details:
            </label>
            <input
              type="text"
              placeholder="e.g. Applied via referral / LinkedIn"
              value={notes}
              onChange={e => setNotes(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px',
                borderRadius: 10,
                border: '1px solid #cbd5e1',
                fontSize: 13,
                outline: 'none',
                boxSizing: 'border-box'
              }}
              autoFocus
            />
          </div>
        )}

        {/* Action Buttons */}
        <div style={{ display: 'flex', gap: 10, flexDirection: 'column' }}>
          <button
            type="button"
            onClick={handleConfirmApplied}
            disabled={loading}
            style={{
              width: '100%',
              padding: '12px 18px',
              borderRadius: 14,
              background: 'linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%)',
              color: '#ffffff',
              border: 'none',
              fontSize: 14,
              fontWeight: 700,
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              boxShadow: '0 8px 18px rgba(124, 58, 237, 0.28)',
              transition: 'all 0.15s ease'
            }}
          >
            {loading ? (
              <>
                <span style={{ display: 'inline-block', animation: 'spin 1s linear infinite' }}>⏳</span>
                Adding to Pipeline...
              </>
            ) : (
              <>
                <span>✓</span> Yes, I Applied!
              </>
            )}
          </button>

          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            style={{
              width: '100%',
              padding: '10px 18px',
              borderRadius: 14,
              background: '#f8fafc',
              color: '#64748b',
              border: '1px solid #e2e8f0',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer'
            }}
          >
            Not yet / Just browsing
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
