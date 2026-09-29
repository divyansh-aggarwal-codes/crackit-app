import { useState } from "react";
import { createPortal } from "react-dom";
import { API_BASE_URL } from '../api/axios';
import { useAuth } from '../context/AuthContext';

const API = `${API_BASE_URL}/api`;

const token = () => localStorage.getItem("token");

const authHeaders = () => ({
  Authorization: `Bearer ${token()}`,
  "Content-Type": "application/json",
});

function ScoreRing({ score }) {
  const color =
    score >= 80 ? "#10b981" :
    score >= 60 ? "#f59e0b" :
    "#ef4444";

  const deg = (score / 100) * 360;

  return (
    <div
      style={{
        width: 84,
        height: 84,
        borderRadius: "50%",
        flexShrink: 0,
        background: `conic-gradient(${color} ${deg}deg, #f0eeff 0deg)`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <div
        style={{
          width: 62,
          height: 62,
          borderRadius: "50%",
          background: "#fff",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <span
          style={{
            fontSize: 18,
            fontWeight: 800,
            color,
            lineHeight: 1,
          }}
        >
          {score}%
        </span>

        <span
          style={{
            fontSize: 9,
            color: "#a094c4",
            marginTop: 2,
          }}
        >
          match
        </span>
      </div>
    </div>
  );
}

function ChipList({
  items,
  color = "#5b21b6",
  bg = "#ede9fe",
  icon = null,
}) {
  if (!items?.length) {
    return (
      <span
        style={{
          fontSize: 12,
          color: "#c4b5fd",
        }}
      >
        None
      </span>
    );
  }

  return (
    <div
      style={{
        display: "flex",
        flexWrap: "wrap",
        gap: 7,
      }}
    >
      {items.map((item) => (
        <span
          key={item}
          style={{
            fontSize: 11,
            fontWeight: 600,
            padding: "4px 10px",
            borderRadius: 999,
            background: bg,
            color,
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
          }}
        >
          {icon && <i className={icon} style={{ fontSize: 11 }} />}
          {item}
        </span>
      ))}
    </div>
  );
}

export default function QuickScanModal({
  onClose,
  onSaveAsJob,
}) {
  const { openUpgradeModal, isPro, aiUsageCount, refreshProfile } = useAuth();
  const [jdText, setJdText] = useState("");
  const [scanning, setScanning] = useState(false);
  const [tailoring, setTailoring] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [result, setResult] = useState(null);
  const [tailoredData, setTailoredData] = useState(null);
  const [activeTab, setActiveTab] = useState("analysis");
  const [template, setTemplate] = useState("compact");
  const [tailoringMode, setTailoringMode] = useState("strict");
  const [error, setError] = useState("");
  const [quotaExceeded, setQuotaExceeded] = useState(false);

  const handleScan = async () => {
    if (!jdText.trim()) return;

    setScanning(true);
    setError("");
    setResult(null);
    setTailoredData(null);
    setActiveTab("analysis");
    setQuotaExceeded(false);

    try {
      const res = await fetch(`${API}/ai/quick-scan`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({ jdText }),
      });

      if (res.ok) {
        setResult(await res.json());
        if (refreshProfile) refreshProfile();
      } else {
        const errData = await res.json().catch(() => ({}));
        if (res.status === 402 || errData.error === 'QUOTA_EXCEEDED' || errData.upgradeRequired) {
          setError(errData.message || "You have reached your limit of 3 free AI generations. Upgrade to Pro for unlimited AI features.");
          setQuotaExceeded(true);
        } else {
          setError(errData.message || "Scan failed. Make sure your resume is uploaded.");
        }
      }
    } catch (e) {
      setError("Something went wrong. Try again.");
    }

    setScanning(false);
  };

  const handleQuickTailor = async (overrideMode) => {
    if (!jdText.trim()) return;

    const modeToUse = (typeof overrideMode === 'string' && overrideMode) ? overrideMode : tailoringMode;
    setTailoring(true);
    setError("");
    setQuotaExceeded(false);

    try {
      const res = await fetch(`${API}/ai/quick-tailor`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({ jdText, mode: modeToUse }),
      });

      if (res.ok) {
        const data = await res.json();
        setTailoredData(data);
        if (data.scan) setResult(data.scan);
        setActiveTab("tailor");
        if (refreshProfile) refreshProfile();
      } else {
        const errData = await res.json().catch(() => ({}));
        if (res.status === 402 || errData.error === 'QUOTA_EXCEEDED' || errData.upgradeRequired) {
          setError(errData.message || "AI limit reached. Upgrade to Pro for unlimited features.");
          setQuotaExceeded(true);
        } else {
          setError(errData.message || "Failed to tailor resume. Ensure your master resume is complete.");
        }
      }
    } catch (e) {
      setError("Something went wrong while tailoring. Try again.");
    }

    setTailoring(false);
  };

  const handleDownloadPdf = async () => {
    if (!tailoredData) return;
    setDownloading(true);
    try {
      const res = await fetch(`${API}/ai/quick-tailor/download`, {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({ ...tailoredData, template }),
      });

      if (res.ok) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const cleanName = (tailoredData?.fullName || "Candidate").trim().replace(/\s+/g, "_").replace(/[^a-zA-Z0-9_]/g, "");
        const a = document.createElement("a");
        a.href = url;
        a.download = `${cleanName}_Resume.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      } else {
        setError("Download failed. Please try again.");
      }
    } catch {
      setError("Failed to download PDF.");
    }
    setDownloading(false);
  };

  const handleBulletChange = (expIdx, bIdx, text) => {
    setTailoredData((prev) => {
      const copy = JSON.parse(JSON.stringify(prev));
      copy.tailoredExperiences[expIdx].bullets[bIdx].bulletText = text;
      return copy;
    });
  };

  const handleDeleteBullet = (expIdx, bIdx) => {
    setTailoredData((prev) => {
      const copy = JSON.parse(JSON.stringify(prev));
      copy.tailoredExperiences[expIdx].bullets.splice(bIdx, 1);
      return copy;
    });
  };

  const handleAddBullet = (expIdx) => {
    setTailoredData((prev) => {
      const copy = JSON.parse(JSON.stringify(prev));
      if (!copy.tailoredExperiences[expIdx].bullets) {
        copy.tailoredExperiences[expIdx].bullets = [];
      }
      copy.tailoredExperiences[expIdx].bullets.push({ bulletText: "", technologies: "" });
      return copy;
    });
  };

  return createPortal(
    <>
      <style>
        {`
          .qs-overlay {
            position: fixed;
            inset: 0;
            z-index: 99999;

            background:
              radial-gradient(circle at top, rgba(124,58,237,0.18), transparent 45%),
              rgba(10, 6, 24, 0.72);

            backdrop-filter: blur(10px);
            -webkit-backdrop-filter: blur(10px);

            display: flex;
            align-items: flex-start;
            justify-content: center;

            padding: 34px 18px;
            overflow-y: auto;
          }

          .qs-modal {
            width: 100%;
            max-width: 760px;

            background:
              linear-gradient(
                to bottom,
                rgba(255,255,255,0.98),
                rgba(252,249,255,0.98)
              );

            border-radius: 28px;

            border: 1px solid rgba(255,255,255,0.10);

            box-shadow:
              0 30px 90px rgba(0,0,0,0.34),
              0 0 0 1px rgba(139,92,246,0.05);

            overflow: hidden;

            animation: qsEnter 0.22s ease;
          }

          @keyframes qsEnter {
            from {
              opacity: 0;
              transform: translateY(18px) scale(0.98);
            }
            to {
              opacity: 1;
              transform: translateY(0) scale(1);
            }
          }

          .qs-header {
            display: flex;
            align-items: flex-start;
            justify-content: space-between;

            gap: 18px;

            padding: 26px 30px 20px;

            background:
              linear-gradient(
                to bottom,
                rgba(124,58,237,0.05),
                transparent
              );
          }

          .qs-header h2 {
            font-size: 1.45rem;
            font-weight: 800;
            color: #1a1040;
            margin: 0;
          }

          .qs-header p {
            font-size: 0.85rem;
            color: #8b7bb4;
            margin: 7px 0 0;
            line-height: 1.6;
          }

          .qs-body {
            padding: 0 30px 24px;
          }

          .qs-footer {
            padding: 18px 30px 24px;

            border-top: 1px solid rgba(139,92,246,0.08);

            display: flex;
            gap: 10px;
            flex-wrap: wrap;

            background: rgba(255,255,255,0.72);

            backdrop-filter: blur(8px);
          }

          .qs-textarea {
            width: 100%;
            min-height: 180px;

            padding: 1rem;

            border: 1.5px solid #e4daff;
            border-radius: 14px;

            font-size: 0.9rem;
            font-family: inherit;

            color: #1a1040;
            background: #fdfcff;

            resize: vertical;
            outline: none;

            line-height: 1.7;

            transition: all 0.15s ease;

            box-sizing: border-box;
          }

          .qs-textarea:focus {
            border-color: #7c3aed;
            box-shadow: 0 0 0 4px rgba(124,58,237,0.08);
          }

          .qs-result {
            margin-top: 1.5rem;
          }

          .qs-result-header {
            display: flex;
            align-items: center;
            gap: 1.4rem;

            padding: 1.2rem 1.3rem;

            background: #faf8ff;

            border-radius: 16px;
            border: 1px solid #ede9fe;

            margin-bottom: 1.4rem;
          }

          .qs-result-meta {
            flex: 1;
          }

          .qs-result-level {
            font-size: 0.78rem;
            font-weight: 700;

            color: #7c3aed;
            background: #ede9fe;

            padding: 4px 12px;
            border-radius: 999px;

            display: inline-block;
            margin-bottom: 0.7rem;
          }

          .qs-result-summary {
            font-size: 0.88rem;
            color: #555;
            line-height: 1.7;
          }

          .qs-section {
            margin-bottom: 1.2rem;
          }

          .qs-section-label {
            font-size: 0.72rem;

            text-transform: uppercase;
            letter-spacing: 0.08em;

            font-weight: 800;
            color: #a094c4;

            margin-bottom: 0.6rem;
          }

          .qs-error {
            display: flex;
            align-items: center;
            gap: 7px;

            background: #fff1f1;
            border: 1px solid #fecaca;

            border-radius: 10px;

            padding: 10px 14px;

            margin-top: 14px;

            font-size: 13px;
            color: #ef4444;
          }

          .qs-spinner {
            width: 40px;
            height: 40px;

            border-radius: 50%;

            border: 3px solid #ede9fe;
            border-top-color: #7c3aed;

            animation: spin 0.8s linear infinite;

            margin: 2.2rem auto 0;
          }

          @keyframes spin {
            to {
              transform: rotate(360deg);
            }
          }

          .qs-scanning-text {
            text-align: center;
            color: #a094c4;
            font-size: 0.88rem;
            margin-top: 0.8rem;
          }

          .btn-primary {
            display: inline-flex;
            align-items: center;
            gap: 0.45rem;

            padding: 0.75rem 1.3rem;

            background:
              linear-gradient(
                135deg,
                #7c3aed,
                #8b5cf6
              );

            color: #fff;

            border: none;
            border-radius: 12px;

            font-size: 0.88rem;
            font-family: inherit;
            font-weight: 700;

            cursor: pointer;

            box-shadow:
              0 10px 24px rgba(124,58,237,0.24);

            transition: all 0.15s ease;
          }

          .btn-primary:hover {
            transform: translateY(-1px);
          }

          .btn-primary:disabled {
            opacity: 0.65;
            cursor: not-allowed;
          }

          .btn-ghost {
            display: inline-flex;
            align-items: center;
            gap: 0.45rem;

            padding: 0.72rem 1rem;

            background: #fff;
            color: #7c6faa;

            border: 1px solid #e0d9ff;
            border-radius: 12px;

            font-size: 0.88rem;
            font-family: inherit;
            font-weight: 600;

            cursor: pointer;

            transition: all 0.15s ease;
          }

          .btn-ghost:hover {
            background: #f5f0ff;
            color: #1a1040;
          }

          .btn-icon-close {
            background: rgba(124,58,237,0.08);

            border: none;

            cursor: pointer;

            width: 38px;
            height: 38px;

            border-radius: 14px;

            color: #7c3aed;

            display: flex;
            align-items: center;
            justify-content: center;

            transition: all 0.15s ease;
          }

          .btn-icon-close:hover {
            background: rgba(124,58,237,0.14);
          }

          @media (max-width: 720px) {
            .qs-overlay {
              padding: 16px;
            }

            .qs-modal {
              border-radius: 22px;
            }

            .qs-header,
            .qs-body,
            .qs-footer {
              padding-left: 18px;
              padding-right: 18px;
            }

            .qs-result-header {
              flex-direction: column;
              align-items: flex-start;
            }
          }
        `}
      </style>

      <div
        className="qs-overlay"
        onClick={onClose}
      >
        <div
          className="qs-modal"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="qs-header">
            <div>
              <h2>⚡ Quick JD Scan</h2>

              <p>
                Paste any job description — get instant match score
                and skill analysis
              </p>
            </div>

            <button
              className="btn-icon-close"
              onClick={onClose}
            >
              <i
                className="ti ti-x"
                style={{ fontSize: 18 }}
              />
            </button>
          </div>

          <div className="qs-body">
            {!isPro && (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10, fontSize: 12, color: '#7c6faa' }}>
                <span>Free Plan: <strong style={{ color: aiUsageCount >= 3 ? '#ef4444' : '#6d28d9' }}>{aiUsageCount}/3</strong> free AI scans used</span>
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    openUpgradeModal();
                  }}
                  style={{ background: 'none', border: 'none', color: '#7c3aed', fontWeight: 700, cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                >
                  ⚡ Upgrade to Unlimited
                </button>
              </div>
            )}

            <textarea
              className="qs-textarea"
              placeholder={`Paste the job description here...

e.g. We are looking for a Senior Java Backend Engineer with 3+ years of experience in Spring Boot and Microservices...`}
              value={jdText}
              onChange={(e) => setJdText(e.target.value)}
              autoFocus
            />

            {!result && !tailoredData && (
              <div style={{
                marginTop: 12,
                padding: '12px 14px',
                background: '#fcfaff',
                border: '1px solid #ebdffd',
                borderRadius: 12,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span style={{ fontSize: 12, fontWeight: 700, color: '#3b0764' }}>
                    ATS Tailoring Mode
                  </span>
                  <span style={{ fontSize: 11, color: '#7c6faa', fontWeight: 600 }}>
                    {tailoringMode === 'aggressive' ? '⚡ +8% to +15% Match Boost' : '🛡️ 100% Profile Truth'}
                  </span>
                </div>

                <div style={{
                  display: 'flex',
                  gap: 6,
                  background: '#ede9fe',
                  padding: 3,
                  borderRadius: 10,
                }}>
                  <button
                    type="button"
                    onClick={() => setTailoringMode("strict")}
                    style={{
                      flex: 1,
                      padding: '7px 10px',
                      borderRadius: 8,
                      border: 'none',
                      background: tailoringMode === 'strict' ? '#ffffff' : 'transparent',
                      color: tailoringMode === 'strict' ? '#1e1b4b' : '#6b7280',
                      fontWeight: tailoringMode === 'strict' ? 700 : 500,
                      fontSize: 12,
                      cursor: 'pointer',
                      boxShadow: tailoringMode === 'strict' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 6,
                      transition: 'all 0.15s ease'
                    }}
                  >
                    <span>🛡️ Strict Profile</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setTailoringMode("aggressive")}
                    style={{
                      flex: 1,
                      padding: '7px 10px',
                      borderRadius: 8,
                      border: 'none',
                      background: tailoringMode === 'aggressive' ? '#ffffff' : 'transparent',
                      color: tailoringMode === 'aggressive' ? '#6d28d9' : '#6b7280',
                      fontWeight: tailoringMode === 'aggressive' ? 700 : 500,
                      fontSize: 12,
                      cursor: 'pointer',
                      boxShadow: tailoringMode === 'aggressive' ? '0 1px 3px rgba(124,58,237,0.2)' : 'none',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 6,
                      transition: 'all 0.15s ease'
                    }}
                  >
                    <span>⚡ Aggressive ATS Boost</span>
                    <span style={{
                      fontSize: 10,
                      background: '#dcfce7',
                      color: '#15803d',
                      fontWeight: 800,
                      padding: '1px 5px',
                      borderRadius: 5
                    }}>
                      Boost
                    </span>
                  </button>
                </div>

                <div style={{ fontSize: 11, color: '#6b7280', marginTop: 6, lineHeight: 1.4 }}>
                  {tailoringMode === 'strict' ? (
                    <span>🛡️ <strong>Strict Truth:</strong> Uses only tools present in your master profile. Zero invented tools.</span>
                  ) : (
                    <span>⚡ <strong>Aggressive Maximizer:</strong> Strategically bridges 2-3 adjacent, fast-learn skills (e.g. Kubernetes, Testcontainers) to pass strict ATS filters, with interview prep study tips.</span>
                  )}
                </div>
              </div>
            )}

            {error && (
              <div className="qs-error" style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-start' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <i
                    className="ti ti-alert-circle"
                    style={{ fontSize: 14 }}
                  />
                  <span>{error}</span>
                </div>
                {quotaExceeded && (
                  <button
                    type="button"
                    onClick={() => {
                      onClose();
                      openUpgradeModal();
                    }}
                    style={{
                      padding: '6px 14px',
                      borderRadius: 10,
                      background: 'linear-gradient(135deg, #7c3aed, #6d28d9)',
                      color: '#ffffff',
                      border: 'none',
                      fontSize: 12,
                      fontWeight: 700,
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      boxShadow: '0 2px 8px rgba(124, 58, 237, 0.3)'
                    }}
                  >
                    <i className="ti ti-crown" /> Upgrade to Pro for Unlimited Scans
                  </button>
                )}
              </div>
            )}

            {scanning && (
              <div>
                <div className="qs-spinner" />
                <p className="qs-scanning-text">
                  Analysing JD against your resume...
                </p>
              </div>
            )}

            {tailoring && (
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <div className="qs-spinner" />
                <p className="qs-scanning-text">
                  ⚡ Tailoring your resume bullets with Google X-Y-Z formula...
                </p>
              </div>
            )}

            {tailoredData && !scanning && !tailoring && (
              <div style={{ display: 'flex', gap: 10, marginTop: 18, marginBottom: 14 }}>
                <button
                  type="button"
                  onClick={() => setActiveTab('analysis')}
                  style={{
                    padding: '8px 16px',
                    borderRadius: 10,
                    border: 'none',
                    background: activeTab === 'analysis' ? '#7c3aed' : '#f0eeff',
                    color: activeTab === 'analysis' ? '#fff' : '#6d28d9',
                    fontWeight: 700,
                    fontSize: 12,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}
                >
                  <i className="ti ti-chart-bar" /> JD Analysis
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('tailor')}
                  style={{
                    padding: '8px 16px',
                    borderRadius: 10,
                    border: 'none',
                    background: activeTab === 'tailor' ? '#7c3aed' : '#f0eeff',
                    color: activeTab === 'tailor' ? '#fff' : '#6d28d9',
                    fontWeight: 700,
                    fontSize: 12,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6
                  }}
                >
                  <i className="ti ti-file-text" /> 📄 Tailored Resume
                </button>
              </div>
            )}

            {result && !scanning && !tailoring && activeTab === 'analysis' && (
              <div className="qs-result">
                <div className="qs-result-header">
                  {result.matchScore != null && (
                    <ScoreRing score={result.matchScore} />
                  )}

                  <div className="qs-result-meta">
                    {result.experienceLevel && (
                      <div className="qs-result-level">
                        {result.experienceLevel}
                      </div>
                    )}

                    {result.summary && (
                      <p className="qs-result-summary">
                        {result.summary}
                      </p>
                    )}
                  </div>
                </div>

                {result.matchedKeywords?.length > 0 && (
                  <div className="qs-section">
                    <div className="qs-section-label" style={{ color: '#059669', display: 'flex', alignItems: 'center', gap: 5 }}>
                      <i className="ti ti-check" /> Matched In Your Resume ({result.matchedKeywords.length})
                    </div>
                    <ChipList
                      items={result.matchedKeywords}
                      bg="#ecfdf5"
                      color="#047857"
                      icon="ti ti-check"
                    />
                  </div>
                )}

                {result.missingKeywords?.length > 0 && (
                  <div className="qs-section">
                    <div className="qs-section-label" style={{ color: '#dc2626', display: 'flex', alignItems: 'center', gap: 5 }}>
                      <i className="ti ti-alert-triangle" /> Missing From Resume ({result.missingKeywords.length})
                    </div>
                    <ChipList
                      items={result.missingKeywords}
                      bg="#fef2f2"
                      color="#b91c1c"
                      icon="ti ti-alert-triangle"
                    />
                  </div>
                )}

                <div className="qs-section">
                  <div className="qs-section-label">
                    Required Skills
                  </div>
                  <ChipList
                    items={result.requiredSkills}
                    bg="#ede9fe"
                    color="#5b21b6"
                  />
                </div>

                <div className="qs-section">
                  <div className="qs-section-label">
                    Preferred Skills
                  </div>
                  <ChipList
                    items={result.preferredSkills}
                    bg="#f5f3ff"
                    color="#6d28d9"
                  />
                </div>

                <div className="qs-section">
                  <div className="qs-section-label">
                    All ATS Keywords
                  </div>
                  <ChipList
                    items={result.atsKeywords}
                    bg="#f0fdf4"
                    color="#065f46"
                  />
                </div>

                {result.importantTopics?.length > 0 && (
                  <div className="qs-section">
                    <div className="qs-section-label">
                      Interview Topics
                    </div>
                    <ChipList
                      items={result.importantTopics}
                      bg="#fef3c7"
                      color="#92400e"
                    />
                  </div>
                )}

                {!tailoredData && (
                  <div style={{
                    marginTop: 20,
                    padding: '16px 20px',
                    borderRadius: 16,
                    background: 'linear-gradient(135deg, rgba(124,58,237,0.08), rgba(167,139,250,0.12))',
                    border: '1px solid rgba(124,58,237,0.2)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 12
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
                      <div>
                        <div style={{ fontWeight: 800, fontSize: 14, color: '#1a1040' }}>⚡ Ready to apply for this job?</div>
                        <div style={{ fontSize: 12, color: '#7c6faa', marginTop: 3 }}>
                          Tailor your resume bullets with Google X-Y-Z formula and generate a clean 1-page PDF.
                        </div>
                      </div>
                      <button
                        className="btn-primary"
                        onClick={() => handleQuickTailor(tailoringMode)}
                        disabled={tailoring}
                        style={{
                          background: tailoringMode === 'aggressive'
                            ? 'linear-gradient(135deg, #7c3aed, #6d28d9)'
                            : 'linear-gradient(135deg, #059669, #10b981)'
                        }}
                      >
                        <i className="ti ti-wand" /> {tailoringMode === 'aggressive' ? '⚡ Fast Tailor (Aggressive Boost)' : '🛡️ Fast Tailor Resume'}
                      </button>
                    </div>

                    <div style={{
                      display: 'flex',
                      gap: 6,
                      background: 'rgba(237, 233, 254, 0.7)',
                      padding: 3,
                      borderRadius: 10,
                    }}>
                      <button
                        type="button"
                        onClick={() => setTailoringMode("strict")}
                        style={{
                          flex: 1,
                          padding: '6px 10px',
                          borderRadius: 8,
                          border: 'none',
                          background: tailoringMode === 'strict' ? '#ffffff' : 'transparent',
                          color: tailoringMode === 'strict' ? '#1e1b4b' : '#6b7280',
                          fontWeight: tailoringMode === 'strict' ? 700 : 500,
                          fontSize: 11.5,
                          cursor: 'pointer',
                          boxShadow: tailoringMode === 'strict' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: 6,
                          transition: 'all 0.15s ease'
                        }}
                      >
                        <span>🛡️ Strict Profile (Exact Match)</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setTailoringMode("aggressive")}
                        style={{
                          flex: 1,
                          padding: '6px 10px',
                          borderRadius: 8,
                          border: 'none',
                          background: tailoringMode === 'aggressive' ? '#ffffff' : 'transparent',
                          color: tailoringMode === 'aggressive' ? '#6d28d9' : '#6b7280',
                          fontWeight: tailoringMode === 'aggressive' ? 700 : 500,
                          fontSize: 11.5,
                          cursor: 'pointer',
                          boxShadow: tailoringMode === 'aggressive' ? '0 1px 3px rgba(124,58,237,0.2)' : 'none',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: 6,
                          transition: 'all 0.15s ease'
                        }}
                      >
                        <span>⚡ Aggressive ATS Boost</span>
                        <span style={{
                          fontSize: 10,
                          background: '#dcfce7',
                          color: '#15803d',
                          fontWeight: 800,
                          padding: '1px 5px',
                          borderRadius: 5
                        }}>
                          +8-15%
                        </span>
                      </button>
                    </div>

                    <div style={{ fontSize: 11, color: '#6b7280', lineHeight: 1.4 }}>
                      {tailoringMode === 'strict' ? (
                        <span>🛡️ Strictly uses tools on your profile. Zero ungrounded skills.</span>
                      ) : (
                        <span>⚡ Bridges 2-3 adjacent, easily learnable missing JD skills into your resume bullets to boost your score by +8% to +15%, with interview study notes.</span>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}

            {tailoredData && !scanning && !tailoring && activeTab === 'tailor' && (
              <div className="qs-result">
                {/* Mode Switcher Bar */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  background: '#f8fafc',
                  padding: '10px 14px',
                  borderRadius: 12,
                  border: '1px solid #e2e8f0',
                  marginBottom: 14,
                  flexWrap: 'wrap',
                  gap: 10
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 12, fontWeight: 700, color: '#475569' }}>Active Mode:</span>
                    <span style={{
                      fontSize: 12,
                      fontWeight: 700,
                      color: tailoredData.injectedSkills?.length > 0 ? '#7c3aed' : '#059669',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4
                    }}>
                      {tailoredData.injectedSkills?.length > 0 ? '⚡ Aggressive ATS Boost' : '🛡️ Strict Profile Truth'}
                    </span>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      const nextMode = tailoredData.injectedSkills?.length > 0 ? 'strict' : 'aggressive';
                      setTailoringMode(nextMode);
                      handleQuickTailor(nextMode);
                    }}
                    disabled={tailoring}
                    style={{
                      background: tailoredData.injectedSkills?.length > 0 ? '#ede9fe' : 'linear-gradient(135deg, #7c3aed, #6d28d9)',
                      color: tailoredData.injectedSkills?.length > 0 ? '#6d28d9' : '#ffffff',
                      border: 'none',
                      borderRadius: 8,
                      padding: '6px 12px',
                      fontSize: 11.5,
                      fontWeight: 700,
                      cursor: 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6
                    }}
                  >
                    {tailoredData.injectedSkills?.length > 0
                      ? '🛡️ Re-tailor in Strict Mode'
                      : '⚡ Re-tailor with Aggressive Boost (+8-15%)'}
                  </button>
                </div>

                {/* Injected Skills Notice Banner with interview prep notes */}
                {tailoredData.injectedSkills?.length > 0 && (
                  <div style={{
                    padding: '14px 16px',
                    borderRadius: 12,
                    background: '#fffbeb',
                    border: '1px solid #fde68a',
                    marginBottom: 14
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 6 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 800, color: '#92400e', fontSize: 13 }}>
                        <span>⚡</span>
                        <span>ATS Maximizer: {tailoredData.injectedSkills.length} Fast-Learn Skills Bridged</span>
                      </div>
                      <span style={{
                        fontSize: 10.5,
                        fontWeight: 700,
                        background: '#fef3c7',
                        color: '#b45309',
                        padding: '2px 8px',
                        borderRadius: 10,
                        border: '1px solid #fcd34d'
                      }}>
                        Passes Strict ATS Filters
                      </span>
                    </div>

                    <p style={{ fontSize: 11.5, color: '#78350f', margin: '6px 0 10px 0', lineHeight: 1.4 }}>
                      These adjacent skills were bridged from the JD into your resume because they are natural extensions of your stack and easily learnable in a few days. Review these study notes before your interview:
                    </p>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      {tailoredData.injectedSkills.map((sk, idx) => (
                        <div key={idx} style={{
                          background: '#ffffff',
                          border: '1px solid #fef08a',
                          borderRadius: 8,
                          padding: '8px 12px',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 2
                        }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <span style={{
                              fontSize: 11,
                              fontWeight: 700,
                              background: '#ecfdf5',
                              color: '#065f46',
                              padding: '2px 6px',
                              borderRadius: 4
                            }}>
                              {sk}
                            </span>
                            <span style={{ fontSize: 10.5, color: '#a16207', fontWeight: 600 }}>Fast-Learn Bridge</span>
                          </div>
                          {tailoredData.learningNotes?.[idx] && (
                            <div style={{ fontSize: 11.5, color: '#451a03', marginTop: 2, lineHeight: 1.35 }}>
                              💡 <strong>Interview Prep:</strong> {tailoredData.learningNotes[idx]}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div style={{
                  padding: '14px 18px',
                  borderRadius: 14,
                  background: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 16
                }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#166534' }}>
                      ✓ Tailored with Google X-Y-Z Formula
                    </div>
                    <div style={{ fontSize: 11, color: '#15803d', marginTop: 2 }}>
                      Review, tweak, or delete any bullet before downloading your PDF.
                    </div>
                  </div>
                  {tailoredData.matchScore != null && (
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: 15, fontWeight: 800, color: '#16a34a' }}>
                        {tailoredData.matchScore}% Match
                      </div>
                      {tailoredData.injectedSkills?.length > 0 && (
                        <div style={{ fontSize: 10, fontWeight: 700, color: '#d97706' }}>
                          ⚡ Boosted ATS Score
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {tailoredData.tailoredSummary && (
                  <div className="qs-section">
                    <div className="qs-section-label">Tailored Summary (Editable)</div>
                    <textarea
                      value={tailoredData.tailoredSummary}
                      onChange={(e) => setTailoredData(prev => ({ ...prev, tailoredSummary: e.target.value }))}
                      style={{
                        width: '100%',
                        minHeight: 70,
                        padding: 10,
                        borderRadius: 10,
                        border: '1px solid #e4daff',
                        fontFamily: 'inherit',
                        fontSize: 12.5,
                        color: '#1a1040',
                        boxSizing: 'border-box'
                      }}
                    />
                  </div>
                )}

                <div className="qs-section">
                  <div className="qs-section-label">Curated Skills</div>
                  <ChipList items={tailoredData.tailoredSkills} bg="#ede9fe" color="#5b21b6" />
                </div>

                <div className="qs-section">
                  <div className="qs-section-label">Experience Bullets (In-line Editable)</div>
                  {tailoredData.tailoredExperiences?.map((exp, expIdx) => (
                    <div key={expIdx} style={{ marginBottom: 14, padding: '12px 14px', borderRadius: 12, background: '#faf9ff', border: '1px solid #ede9fe' }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: '#1a1040', marginBottom: 8 }}>
                        {exp.role} — <span style={{ color: '#6d28d9' }}>{exp.companyName}</span>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        {exp.bullets?.map((b, bIdx) => (
                          <div key={bIdx} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            <textarea
                              value={b.bulletText}
                              onChange={(e) => handleBulletChange(expIdx, bIdx, e.target.value)}
                              style={{
                                flex: 1,
                                minHeight: 48,
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
                              <i className="ti ti-trash" style={{ fontSize: 13 }} />
                            </button>
                          </div>
                        ))}
                      </div>

                      <button
                        type="button"
                        onClick={() => handleAddBullet(expIdx)}
                        style={{
                          marginTop: 8,
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
                    </div>
                  ))}
                </div>

                <div className="qs-section">
                  <div className="qs-section-label">ATS Keywords Embedded</div>
                  <ChipList items={tailoredData.atsKeywordsUsed} bg="#f0fdf4" color="#065f46" icon="ti ti-check" />
                </div>
              </div>
            )}
          </div>

          <div className="qs-footer">
            {!result && !tailoredData ? (
              <>
                <button
                  className="btn-primary"
                  onClick={handleScan}
                  disabled={scanning || !jdText.trim()}
                >
                  {scanning ? (
                    <>
                      <i className="ti ti-loader ti-spin" />
                      Scanning...
                    </>
                  ) : (
                    <>
                      <i className="ti ti-sparkles" />
                      Scan JD
                    </>
                  )}
                </button>

                <button
                  className="btn-primary"
                  onClick={() => handleQuickTailor(tailoringMode)}
                  disabled={tailoring || scanning || !jdText.trim()}
                  style={{
                    background: tailoringMode === 'aggressive'
                      ? 'linear-gradient(135deg, #7c3aed, #6d28d9)'
                      : 'linear-gradient(135deg, #059669, #10b981)'
                  }}
                >
                  {tailoring ? (
                    <>
                      <i className="ti ti-loader ti-spin" />
                      Tailoring...
                    </>
                  ) : (
                    <>
                      <i className="ti ti-wand" />
                      {tailoringMode === 'aggressive' ? '⚡ 1-Click Boost Tailor' : '⚡ 1-Click Tailor'}
                    </>
                  )}
                </button>

                <button
                  className="btn-ghost"
                  onClick={onClose}
                >
                  Cancel
                </button>
              </>
            ) : (
              <>
                {tailoredData && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <select
                      value={template}
                      onChange={(e) => setTemplate(e.target.value)}
                      style={{
                        fontSize: 12,
                        padding: '8px 10px',
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
                      className="btn-primary"
                      onClick={handleDownloadPdf}
                      disabled={downloading}
                    >
                      {downloading ? (
                        <>
                          <i className="ti ti-loader ti-spin" />
                          Generating PDF...
                        </>
                      ) : (
                        <>
                          <i className="ti ti-download" />
                          Download Tailored PDF
                        </>
                      )}
                    </button>
                  </div>
                )}

                {!tailoredData && (
                  <button
                    className="btn-primary"
                    onClick={() => handleQuickTailor(tailoringMode)}
                    disabled={tailoring}
                    style={{
                      background: tailoringMode === 'aggressive'
                        ? 'linear-gradient(135deg, #7c3aed, #6d28d9)'
                        : 'linear-gradient(135deg, #059669, #10b981)'
                    }}
                  >
                    {tailoring ? (
                      <>
                        <i className="ti ti-loader ti-spin" />
                        Tailoring...
                      </>
                    ) : (
                      <>
                        <i className="ti ti-wand" />
                        {tailoringMode === 'aggressive' ? '⚡ Tailor (Aggressive Boost)' : '⚡ Tailor Resume'}
                      </>
                    )}
                  </button>
                )}

                <button
                  className="btn-ghost"
                  onClick={() => {
                    setResult(null);
                    setTailoredData(null);
                    setJdText("");
                    setActiveTab("analysis");
                  }}
                >
                  <i className="ti ti-refresh" />
                  Scan Another
                </button>

                {onSaveAsJob && (
                  <button
                    className="btn-ghost"
                    onClick={() => onSaveAsJob(jdText)}
                  >
                    <i className="ti ti-plus" />
                    Save as Job
                  </button>
                )}

                <button
                  className="btn-ghost"
                  onClick={onClose}
                >
                  Close
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </>,
    document.body
  );
}