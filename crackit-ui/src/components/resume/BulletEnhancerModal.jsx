import React, { useState, useEffect } from 'react'
import { Sparkles, Check, RefreshCw, X, ArrowRight, ShieldCheck, Zap, Edit3 } from 'lucide-react'
import api from '../../api/axios'

export default function BulletEnhancerModal({
    isOpen,
    onClose,
    bulletText,
    role = '',
    company = '',
    onApply
}) {
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [result, setResult] = useState(null)
    const [selectedText, setSelectedText] = useState('')
    const [isEditing, setIsEditing] = useState(false)
    const [applying, setApplying] = useState(false)

    useEffect(() => {
        if (isOpen && bulletText) {
            handleFetchEnhancement()
        } else {
            setResult(null)
            setSelectedText('')
            setError('')
            setIsEditing(false)
        }
    }, [isOpen, bulletText])

    const handleFetchEnhancement = async () => {
        setLoading(true)
        setError('')
        try {
            const res = await api.post('/api/ai/resume/enhance-bullet', {
                bulletText,
                role,
                company
            })
            const data = res.data
            setResult(data)
            setSelectedText(data.enhanced_bullet || bulletText)
        } catch (err) {
            console.error('Enhance bullet error:', err)
            setError(err.response?.data?.message || 'Failed to enhance bullet with AI. Please try again.')
        } finally {
            setLoading(false)
        }
    }

    const handleApply = async () => {
        if (!selectedText.trim()) return
        setApplying(true)
        try {
            await onApply(selectedText)
            onClose()
            window.dispatchEvent(
                new CustomEvent('crackit:toast', {
                    detail: { type: 'success', message: '✨ Bullet updated with Google X-Y-Z formula!' }
                })
            )
        } catch (err) {
            setError('Failed to apply changes to bullet.')
        } finally {
            setApplying(false)
        }
    }

    if (!isOpen) return null

    return (
        <div
            style={{
                position: 'fixed',
                inset: 0,
                zIndex: 9999,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'rgba(15, 10, 30, 0.65)',
                backdropFilter: 'blur(8px)',
                padding: 16
            }}
            onClick={(e) => {
                if (e.target === e.currentTarget && !applying) onClose()
            }}
        >
            <div
                style={{
                    position: 'relative',
                    width: '100%',
                    maxWidth: 580,
                    maxHeight: '92vh',
                    background: '#ffffff',
                    borderRadius: 22,
                    boxShadow: '0 24px 60px rgba(124, 58, 237, 0.25), 0 8px 24px rgba(0, 0, 0, 0.12)',
                    border: '1px solid rgba(124, 58, 237, 0.15)',
                    overflow: 'hidden',
                    display: 'flex',
                    flexDirection: 'column',
                    fontFamily: 'inherit'
                }}
            >
                {/* Header */}
                <div
                    style={{
                        background: 'linear-gradient(135deg, #7c3aed 0%, #6366f1 50%, #8b5cf6 100%)',
                        padding: '20px 24px 18px',
                        color: '#ffffff',
                        position: 'relative'
                    }}
                >
                    <button
                        onClick={onClose}
                        style={{
                            position: 'absolute',
                            top: 14,
                            right: 14,
                            background: 'rgba(255, 255, 255, 0.2)',
                            border: 'none',
                            color: '#ffffff',
                            borderRadius: '50%',
                            width: 30,
                            height: 30,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer'
                        }}
                    >
                        <X size={15} />
                    </button>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div
                            style={{
                                width: 36,
                                height: 36,
                                borderRadius: 10,
                                background: 'rgba(255, 255, 255, 0.25)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center'
                            }}
                        >
                            <Sparkles size={20} color="#fbbf24" fill="#fbbf24" />
                        </div>
                        <div>
                            <h3 style={{ margin: 0, fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', display: 'flex', alignItems: 'center', gap: 8 }}>
                                <span>AI Bullet Enhancer</span>
                                <span style={{ fontSize: 10, background: '#fef3c7', color: '#b45309', padding: '2px 8px', borderRadius: 99, fontWeight: 800 }}>
                                    Google X-Y-Z
                                </span>
                            </h3>
                            <p style={{ margin: '2px 0 0', fontSize: 12, opacity: 0.9 }}>
                                Transforms average bullets into bar-raising executive impact statements
                            </p>
                        </div>
                    </div>
                </div>

                {/* Body Content */}
                <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
                    {error && (
                        <div style={{ background: '#fef2f2', border: '1px solid #fecaca', color: '#b91c1c', padding: '10px 14px', borderRadius: 10, fontSize: 12.5 }}>
                            {error}
                        </div>
                    )}

                    {loading ? (
                        <div style={{ padding: '40px 0', textAlign: 'center', color: '#64748b' }}>
                            <div style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 48, height: 48, borderRadius: '50%', background: '#f3e8ff', color: '#7c3aed', marginBottom: 12 }}>
                                <RefreshCw size={24} className="animate-spin" />
                            </div>
                            <div style={{ fontWeight: 700, color: '#1e293b', fontSize: 15 }}>
                                Elevating Bullet with Google X-Y-Z Formula...
                            </div>
                            <div style={{ fontSize: 12, color: '#94a3b8', marginTop: 4 }}>
                                Detecting active verbs, injecting quantifiable scale, and sharpening technical mechanisms
                            </div>
                        </div>
                    ) : result ? (
                        <>
                            {/* Before vs After Section */}
                            <div>
                                <div style={{ fontSize: 11, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase', marginBottom: 6 }}>
                                    Original Bullet (Passive):
                                </div>
                                <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 10, padding: '10px 14px', fontSize: 13, color: '#64748b', lineHeight: 1.5 }}>
                                    • {result.original_bullet}
                                </div>
                            </div>

                            <div>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                                    <div style={{ fontSize: 11, fontWeight: 700, color: '#059669', textTransform: 'uppercase', display: 'flex', alignItems: 'center', gap: 6 }}>
                                        <Sparkles size={13} />
                                        <span>Enhanced Google X-Y-Z Bullet:</span>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => setIsEditing(!isEditing)}
                                        style={{ background: 'none', border: 'none', color: '#7c3aed', fontSize: 11.5, fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}
                                    >
                                        <Edit3 size={12} />
                                        <span>{isEditing ? 'Preview' : 'Tweak text'}</span>
                                    </button>
                                </div>

                                {isEditing ? (
                                    <textarea
                                        rows={3}
                                        value={selectedText}
                                        onChange={(e) => setSelectedText(e.target.value)}
                                        style={{
                                            width: '100%',
                                            padding: '10px 12px',
                                            borderRadius: 12,
                                            border: '2px solid #7c3aed',
                                            fontSize: 13.5,
                                            color: '#1e293b',
                                            lineHeight: 1.5,
                                            fontFamily: 'inherit'
                                        }}
                                    />
                                ) : (
                                    <div
                                        style={{
                                            background: '#f0fdf4',
                                            border: '1.5px solid #86efac',
                                            borderRadius: 12,
                                            padding: '12px 14px',
                                            fontSize: 13.5,
                                            fontWeight: 500,
                                            color: '#14532d',
                                            lineHeight: 1.55,
                                            position: 'relative'
                                        }}
                                    >
                                        • {selectedText}
                                    </div>
                                )}
                            </div>

                            {/* Signal Badges */}
                            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                {result.action_verb && (
                                    <div style={{ background: '#ede9fe', color: '#6d28d9', fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 99 }}>
                                        Verb: {result.action_verb}
                                    </div>
                                )}
                                {result.metric_dimension && (
                                    <div style={{ background: '#e0f2fe', color: '#0369a1', fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 99 }}>
                                        Impact: {result.metric_dimension}
                                    </div>
                                )}
                                <div style={{ background: '#dcfce7', color: '#15803d', fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 99 }}>
                                    +35% ATS Signal
                                </div>
                            </div>

                            {/* Google X-Y-Z Breakdown Cards */}
                            {result.formula_breakdown && (
                                <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 12, padding: '12px 14px' }}>
                                    <div style={{ fontSize: 11, fontWeight: 700, color: '#475569', textTransform: 'uppercase', marginBottom: 8 }}>
                                        Google X-Y-Z Formula Breakdown:
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 12 }}>
                                        <div>
                                            <strong style={{ color: '#6d28d9' }}>[X] Accomplished:</strong>{' '}
                                            <span style={{ color: '#334155' }}>{result.formula_breakdown.accomplished_x}</span>
                                        </div>
                                        <div>
                                            <strong style={{ color: '#0284c7' }}>[Y] Measured by:</strong>{' '}
                                            <span style={{ color: '#334155' }}>{result.formula_breakdown.measured_by_y}</span>
                                        </div>
                                        <div>
                                            <strong style={{ color: '#16a34a' }}>[Z] By doing:</strong>{' '}
                                            <span style={{ color: '#334155' }}>{result.formula_breakdown.doing_z}</span>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Alternative Variations */}
                            {result.alternatives && result.alternatives.length > 0 && (
                                <div>
                                    <div style={{ fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', marginBottom: 8 }}>
                                        Alternative Perspectives (Tap to Select):
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                        {result.alternatives.map((alt, idx) => {
                                            const isSelected = selectedText === alt.bullet
                                            return (
                                                <div
                                                    key={idx}
                                                    onClick={() => setSelectedText(alt.bullet)}
                                                    style={{
                                                        padding: '10px 12px',
                                                        borderRadius: 10,
                                                        border: isSelected ? '2px solid #7c3aed' : '1px solid #e2e8f0',
                                                        background: isSelected ? '#faf5ff' : '#ffffff',
                                                        cursor: 'pointer',
                                                        fontSize: 12.5,
                                                        color: isSelected ? '#4c1d95' : '#334155',
                                                        lineHeight: 1.45,
                                                        transition: 'all 0.15s ease'
                                                    }}
                                                >
                                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
                                                        <span style={{ fontSize: 10.5, fontWeight: 800, color: isSelected ? '#7c3aed' : '#64748b', textTransform: 'uppercase' }}>
                                                            {alt.angle}
                                                        </span>
                                                        {isSelected && <Check size={14} color="#7c3aed" />}
                                                    </div>
                                                    • {alt.bullet}
                                                </div>
                                            )
                                        })}
                                    </div>
                                </div>
                            )}
                        </>
                    ) : null}
                </div>

                {/* Footer Actions */}
                <div
                    style={{
                        padding: '14px 24px',
                        borderTop: '1px solid #f1f5f9',
                        background: '#ffffff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 12
                    }}
                >
                    <button
                        type="button"
                        onClick={handleFetchEnhancement}
                        disabled={loading || applying}
                        style={{
                            background: 'none',
                            border: '1px solid #e2e8f0',
                            borderRadius: 10,
                            padding: '8px 14px',
                            color: '#475569',
                            fontSize: 12.5,
                            fontWeight: 600,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6
                        }}
                    >
                        <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
                        <span>Regenerate</span>
                    </button>

                    <div style={{ display: 'flex', gap: 8 }}>
                        <button
                            type="button"
                            onClick={onClose}
                            disabled={applying}
                            style={{
                                background: 'none',
                                border: 'none',
                                color: '#64748b',
                                fontSize: 13,
                                fontWeight: 600,
                                cursor: 'pointer',
                                padding: '8px 14px'
                            }}
                        >
                            Cancel
                        </button>

                        <button
                            type="button"
                            onClick={handleApply}
                            disabled={loading || applying || !selectedText}
                            style={{
                                background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
                                color: '#ffffff',
                                border: 'none',
                                borderRadius: 10,
                                padding: '9px 18px',
                                fontSize: 13,
                                fontWeight: 700,
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                gap: 6,
                                boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)'
                            }}
                        >
                            {applying ? <RefreshCw size={14} className="animate-spin" /> : <Sparkles size={14} />}
                            <span>Apply Enhancement</span>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    )
}
