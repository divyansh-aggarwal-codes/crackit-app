import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios'

export default function NotificationCenter() {
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const dropdownRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    fetchUnreadCount()
    const interval = setInterval(fetchUnreadCount, 60000) // Poll every 60s
    return () => clearInterval(interval)
  }, [])

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const fetchUnreadCount = async () => {
    try {
      const res = await api.get('/api/notifications/unread-count')
      if (res.data) setUnreadCount(res.data.unreadCount || 0)
    } catch (e) {
      // Gracefully ignore network errors on notification poll
    }
  }

  const handleToggle = async () => {
    const nextState = !open
    setOpen(nextState)
    if (nextState) {
      setLoading(true)
      try {
        const res = await api.get('/api/notifications')
        setNotifications(res.data || [])
      } catch (e) {
        console.error(e)
      } finally {
        setLoading(false)
      }
    }
  }

  const handleMarkAsRead = async (id, e) => {
    e?.stopPropagation()
    try {
      await api.put(`/api/notifications/${id}/read`)
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n))
      setUnreadCount(prev => Math.max(0, prev - 1))
    } catch (e) {
      console.error(e)
    }
  }

  const handleMarkAllRead = async () => {
    try {
      await api.put('/api/notifications/read-all')
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })))
      setUnreadCount(0)
    } catch (e) {
      console.error(e)
    }
  }

  const handleNotificationClick = (n) => {
    if (!n.isRead) {
      handleMarkAsRead(n.id)
    }
    setOpen(false)
    if (n.type === 'STRONG_JOB_MATCH') {
      navigate('/discover')
    }
  }

  return (
    <div style={{ position: 'relative' }} ref={dropdownRef}>
      <button
        onClick={handleToggle}
        title="Notifications"
        style={{
          width: 38,
          height: 38,
          borderRadius: 12,
          background: open ? 'rgba(124,58,237,0.15)' : 'rgba(255,255,255,0.85)',
          border: '1px solid rgba(124,58,237,0.18)',
          color: '#7c3aed',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          position: 'relative',
          transition: 'all 0.18s ease'
        }}
      >
        <i className="ti ti-bell" style={{ fontSize: 18 }} />
        {unreadCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: -3,
              right: -3,
              background: '#ef4444',
              color: '#fff',
              fontSize: 10,
              fontWeight: 700,
              borderRadius: 99,
              minWidth: 17,
              height: 17,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 4px',
              border: '2px solid #fff'
            }}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          style={{
            position: 'absolute',
            top: 'calc(100% + 8px)',
            right: 0,
            width: 340,
            maxHeight: 460,
            background: '#fff',
            borderRadius: 16,
            boxShadow: '0 18px 45px rgba(124,58,237,0.18)',
            border: '1px solid rgba(124,58,237,0.12)',
            zIndex: 100,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column'
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '12px 16px',
              borderBottom: '1px solid #f1f0f7',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'linear-gradient(135deg, rgba(124,58,237,0.05), transparent)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontWeight: 700, fontSize: 14, color: '#1a1040' }}>Notifications</span>
              {unreadCount > 0 && (
                <span
                  style={{
                    background: '#ede9fe',
                    color: '#7c3aed',
                    fontSize: 11,
                    fontWeight: 700,
                    padding: '1px 7px',
                    borderRadius: 99
                  }}
                >
                  {unreadCount} new
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                onClick={handleMarkAllRead}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#7c3aed',
                  fontSize: 12,
                  fontWeight: 600,
                  cursor: 'pointer',
                  padding: 0
                }}
              >
                Mark all read
              </button>
            )}
          </div>

          {/* Body */}
          <div style={{ overflowY: 'auto', flex: 1, padding: 8 }}>
            {loading ? (
              <div style={{ padding: '2rem 1rem', textAlign: 'center', color: '#a094c4', fontSize: 13 }}>
                <i className="ti ti-loader" /> Loading alerts...
              </div>
            ) : notifications.length === 0 ? (
              <div style={{ padding: '2.5rem 1rem', textAlign: 'center' }}>
                <i className="ti ti-bell-off" style={{ fontSize: 28, color: '#d4c9f5', display: 'block', marginBottom: 8 }} />
                <p style={{ margin: 0, fontSize: 13, fontWeight: 600, color: '#1a1040' }}>No notifications yet</p>
                <p style={{ margin: '4px 0 0', fontSize: 12, color: '#a094c4' }}>
                  We'll notify you daily when strong matching roles are discovered.
                </p>
              </div>
            ) : (
              notifications.map(n => (
                <div
                  key={n.id}
                  onClick={() => handleNotificationClick(n)}
                  style={{
                    padding: '10px 12px',
                    borderRadius: 10,
                    marginBottom: 4,
                    background: n.isRead ? 'transparent' : 'rgba(124,58,237,0.04)',
                    borderLeft: n.isRead ? '3px solid transparent' : '3px solid #7c3aed',
                    cursor: 'pointer',
                    transition: 'background 0.12s ease'
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = 'rgba(124,58,237,0.08)'}
                  onMouseLeave={e => e.currentTarget.style.background = n.isRead ? 'transparent' : 'rgba(124,58,237,0.04)'}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 6 }}>
                    <div style={{ fontSize: 13, fontWeight: n.isRead ? 600 : 700, color: '#1a1040' }}>
                      {n.title}
                    </div>
                    {!n.isRead && (
                      <span
                        onClick={(e) => handleMarkAsRead(n.id, e)}
                        title="Mark read"
                        style={{ width: 8, height: 8, borderRadius: 99, background: '#7c3aed', flexShrink: 0, marginTop: 4 }}
                      />
                    )}
                  </div>
                  <p style={{ margin: '4px 0 0', fontSize: 12, color: '#6b7280', lineHeight: 1.45 }}>
                    {n.message}
                  </p>
                  <div style={{ fontSize: 10, color: '#9ca3af', marginTop: 4 }}>
                    {new Date(n.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}
