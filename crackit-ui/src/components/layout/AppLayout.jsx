import { Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import Sidebar from './Sidebar'
import MobileBottomNav from './MobileBottomNav'
import UpgradeModal from '../payment/UpgradeModal'
import NotificationCenter from '../NotificationCenter'

export default function AppLayout() {
  const location = useLocation()

  const [visible, setVisible] = useState(false)

  const [isMobile, setIsMobile] = useState(typeof window !== 'undefined' ? window.innerWidth < 768 : false)
  const [isTablet, setIsTablet] = useState(typeof window !== 'undefined' ? window.innerWidth >= 768 && window.innerWidth < 1100 : false)
  const [sidebarOpen, setSidebarOpen] = useState(false)

  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth < 768
      const tablet = window.innerWidth >= 768 && window.innerWidth < 1100

      setIsMobile(mobile)
      setIsTablet(tablet)

      if (!tablet) setSidebarOpen(false)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  useEffect(() => {
    setVisible(false)
    const t = setTimeout(() => setVisible(true), 30)
    return () => clearTimeout(t)
  }, [location.pathname])

  useEffect(() => {
    if (isTablet) {
      setSidebarOpen(false)
    }
  }, [location.pathname])

  return (
    <div
      className="app-bg-pattern"
      style={{
        display: 'flex',
        minHeight: '100vh',
        padding: isMobile ? 0 : 14,
        gap: 0,
        position: 'relative',
        overflowX: 'hidden'
      }}
    >
      {/* Dark overlay */}
      {isTablet && sidebarOpen && (
        <div
          onClick={() => setSidebarOpen(false)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 10, 35, 0.42)',
            backdropFilter: 'blur(3px)',
            zIndex: 40
          }}
        />
      )}

      {!isMobile && (
        <Sidebar
          tablet={isTablet}
          open={sidebarOpen}
          onToggle={() => setSidebarOpen(prev => !prev)}
          onClose={() => setSidebarOpen(false)}
        />
      )}

      <main
        className="smart-scroll"
        style={{
          flex: 1,
          minWidth: 0,
          width: '100%',
          boxSizing: 'border-box',
          marginLeft: isMobile ? 0 : isTablet ? 70 : 0,
          padding: isMobile ? '16px 16px 110px 16px' : '28px 32px',
          background: 'transparent',
          boxShadow: 'none',
          borderRadius: isMobile ? 0 : 18,
          overflowY: 'auto',
          minHeight: '100vh',
          position: 'relative'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
          <NotificationCenter />
        </div>
        
        <div
          style={{
            opacity: visible ? 1 : 0,

            transform: visible
              ? 'translateY(0)'
              : 'translateY(10px)',

            transition:
              'opacity 0.22s ease, transform 0.22s ease'
          }}
        >
          <Outlet />
        </div>
</main>

{isMobile && <MobileBottomNav />}

      <UpgradeModal />
    </div>
  )
}