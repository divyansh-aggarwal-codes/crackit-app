import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { DiscoverSkeleton } from '../components/Skeletons'
import PageHeader from '../components/ui/PageHeader'
import api from '../api/axios'

function SourceBadge({ source, sourceType, atsPlatform }) {
  if (source === "CAREER_PAGE" || sourceType === "CAREER_PAGE_ATS") {
    return (
      <span style={{
        fontSize: 10, fontWeight: 700, padding: "3px 8px",
        borderRadius: 99, background: "#ecfdf5", color: "#065f46",
        border: "1px solid #a7f3d0", letterSpacing: "0.04em",
        display: "inline-flex", alignItems: "center", gap: 4
      }}>
        <i className="ti ti-building" style={{ fontSize: 11 }} />
        {atsPlatform ? `${atsPlatform} Direct` : "Official Career Portal"}
      </span>
    );
  }
  if (source === "GOOGLE_SEARCH" || sourceType === "SEARCH_ENGINE") {
    return (
      <span style={{
        fontSize: 10, fontWeight: 700, padding: "3px 8px",
        borderRadius: 99, background: "#eff6ff", color: "#1e40af",
        border: "1px solid #bfdbfe", letterSpacing: "0.04em",
        display: "inline-flex", alignItems: "center", gap: 4
      }}>
        <i className="ti ti-brand-google" style={{ fontSize: 11 }} />
        Google Search
      </span>
    );
  }
  const styles = {
    ADZUNA: { bg: "#dbeafe", color: "#1e40af", border: "#bfdbfe" },
    JSEARCH: { bg: "#d1fae5", color: "#065f46", border: "#a7f3d0" },
  };
  const s = styles[source] || { bg: "#f0eeff", color: "#5b21b6", border: "#ddd6fe" };
  return (
    <span style={{
      fontSize: 10, fontWeight: 700, padding: "3px 8px",
      borderRadius: 99, background: s.bg, color: s.color,
      border: `1px solid ${s.border}`, letterSpacing: "0.04em"
    }}>
      {source}
    </span>
  );
}

function MatchBadge({ score }) {
  const isStrong = score >= 80;
  const isGood = score >= 60;

  const bg = isStrong ? '#ecfdf5' : isGood ? '#f5f3ff' : '#f8fafc';
  const color = isStrong ? '#047857' : isGood ? '#6d28d9' : '#64748b';
  const border = isStrong ? '#6ee7b7' : isGood ? '#c4b5fd' : '#e2e8f0';

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        padding: '3px 9px',
        borderRadius: 99,
        background: bg,
        color: color,
        border: `1px solid ${border}`,
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.02em'
      }}
    >
      <i className={isStrong ? "ti ti-sparkles" : "ti ti-bolt"} style={{ fontSize: 11 }} />
      {score}% Match {isStrong ? '• Strong Fit' : isGood ? '• Good Fit' : ''}
    </span>
  );
}

function useDebounce(value, delay = 250) {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(t)
  }, [value, delay])

  return debounced
}

function JobCard({ job, onSave, onDismiss }) {
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [dismissing, setDismissing] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      const res = await api.post('/api/jobs', {
        title: job.title,
        companyName: job.company,
        location: job.location,
        jdText: job.description,
        source: job.source,
        applyUrl: job.url,
      });
      if (res.status === 200 || res.status === 201) {
        setSaved(true);
        if (onSave) onSave();
      }
    } catch (e) {
      console.error(e);
    } finally {
      setSaving(false);
    }
  };

  const handleDismiss = async (e) => {
    e.stopPropagation();
    setDismissing(true);
    try {
      await api.post(`/api/jobs/discover/${job.id}/dismiss`);
      if (onDismiss) onDismiss(job.id);
    } catch (e) {
      console.error(e);
      setDismissing(false);
    }
  };

  const isCareerPage = job.source === "CAREER_PAGE" || job.sourceType === "CAREER_PAGE_ATS";

  return (
    <div
      style={{
        background: 'rgba(255,255,255,0.94)',
        backdropFilter: 'blur(10px)',
        borderRadius: 18,
        padding: '1.25rem',
        border: '1px solid rgba(255,255,255,0.65)',
        boxShadow: '0 2px 10px rgba(124,58,237,0.04)',
        transition: 'transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.85rem',
        position: 'relative',
        overflow: 'hidden'
      }}
      onMouseEnter={e => {
        e.currentTarget.style.transform = 'translateY(-2px)'
        e.currentTarget.style.borderColor = 'rgba(196,181,253,0.75)'
        e.currentTarget.style.boxShadow = '0 12px 28px rgba(124,58,237,0.10)'
      }}
      onMouseLeave={e => {
        e.currentTarget.style.transform = 'translateY(0)'
        e.currentTarget.style.borderColor = 'rgba(255,255,255,0.65)'
        e.currentTarget.style.boxShadow = '0 2px 10px rgba(124,58,237,0.04)'
      }}
    >
      {/* Top subtle highlight */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 20,
          right: 20,
          height: 1,
          background: isCareerPage
            ? 'linear-gradient(90deg, transparent, rgba(52,211,153,0.6), transparent)'
            : 'linear-gradient(90deg, transparent, rgba(167,139,250,0.55), transparent)'
        }}
      />

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '0.75rem' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 4 }}>
            <MatchBadge score={job.matchScore || 70} />
            <SourceBadge source={job.source} sourceType={job.sourceType} atsPlatform={job.atsPlatform} />
            {job.isActive && (
              <span style={{ fontSize: 10, color: '#059669', display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                <span style={{ width: 5, height: 5, borderRadius: 99, background: '#10b981' }} /> Verified Active
              </span>
            )}
          </div>

          <div
            style={{
              fontSize: '1.02rem',
              fontWeight: 700,
              color: '#1a1040',
              lineHeight: 1.35,
              letterSpacing: '-0.01em',
              marginTop: 4,
              marginBottom: 4
            }}
          >
            {job.title}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: 8,
                background: 'rgba(124,58,237,0.10)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#7c3aed',
                fontSize: 11,
                fontWeight: 700,
                flexShrink: 0
              }}
            >
              {job.company?.charAt(0) || 'C'}
            </div>
            <div style={{ fontSize: '0.88rem', color: '#7c3aed', fontWeight: 600 }}>
              {job.company}
            </div>
          </div>
        </div>

        {/* Dismiss Button */}
        <button
          onClick={handleDismiss}
          disabled={dismissing}
          title="Dismiss this job"
          style={{
            background: 'none',
            border: 'none',
            color: '#a094c4',
            cursor: 'pointer',
            padding: 4,
            borderRadius: 6,
            transition: 'color 0.15s ease'
          }}
          onMouseEnter={e => e.currentTarget.style.color = '#ef4444'}
          onMouseLeave={e => e.currentTarget.style.color = '#a094c4'}
        >
          <i className="ti ti-eye-off" style={{ fontSize: 16 }} />
        </button>
      </div>

      {/* Meta tags */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", alignItems: 'center' }}>
        {job.location && (
          <span style={{ fontSize: "0.78rem", color: "#7c6faa", display: "flex", alignItems: "center", gap: 4 }}>
            <i className="ti ti-map-pin" style={{ fontSize: 12 }} /> {job.location}
          </span>
        )}
        {job.postedAt && (
          <span style={{ fontSize: "0.78rem", color: "#7c6faa", display: "flex", alignItems: "center", gap: 4 }}>
            <i className="ti ti-clock" style={{ fontSize: 12 }} />
            {new Date(job.postedAt).toLocaleDateString('en-US', { day: 'numeric', month: 'short' })}
          </span>
        )}
        {job.remote && (
          <span style={{ fontSize: "0.75rem", background: "#d1fae5", color: "#065f46", padding: "1px 7px", borderRadius: 99, fontWeight: 600 }}>
            Remote
          </span>
        )}
        {job.jobType && (
          <span style={{ fontSize: "0.75rem", background: "#f0eeff", color: "#5b21b6", padding: "1px 7px", borderRadius: 99 }}>
            {job.jobType}
          </span>
        )}
        {(job.salaryMin || job.salaryMax) && (
          <span style={{ fontSize: "0.78rem", color: "#7c6faa", display: "flex", alignItems: "center", gap: 4 }}>
            <i className="ti ti-currency-rupee" style={{ fontSize: 12 }} />
            {job.salaryMin && job.salaryMax
              ? `${Math.round(job.salaryMin / 1000)}k – ${Math.round(job.salaryMax / 1000)}k`
              : job.salaryMin ? `From ${Math.round(job.salaryMin / 1000)}k` : `Up to ${Math.round(job.salaryMax / 1000)}k`
            }
          </span>
        )}
      </div>

      {/* Skills match breakdown */}
      {job.matchedSkills && job.matchedSkills.length > 0 && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, alignItems: 'center' }}>
          <span style={{ fontSize: 10, color: '#6b7280', fontWeight: 600 }}>Matched Skills:</span>
          {job.matchedSkills.slice(0, 4).map(skill => (
            <span
              key={skill}
              style={{
                fontSize: 10,
                background: 'rgba(16,185,129,0.1)',
                color: '#065f46',
                border: '1px solid rgba(16,185,129,0.25)',
                padding: '1px 6px',
                borderRadius: 6,
                fontWeight: 600,
                display: 'inline-flex',
                alignItems: 'center',
                gap: 3
              }}
            >
              <i className="ti ti-check" style={{ fontSize: 10 }} /> {skill}
            </span>
          ))}
          {job.matchedSkills.length > 4 && (
            <span style={{ fontSize: 10, color: '#6b7280' }}>+{job.matchedSkills.length - 4} more</span>
          )}
        </div>
      )}

      {/* Description snippet */}
      {job.description && (
        <p style={{
          fontSize: "0.82rem", color: "#666", lineHeight: 1.55,
          margin: 0, display: "-webkit-box", WebkitLineClamp: 2,
          WebkitBoxOrient: "vertical", overflow: "hidden"
        }}>
          {job.description}
        </p>
      )}

      {/* Actions */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 4 }}>
        {job.url && (
          <a
            href={job.url}
            target="_blank"
            rel="noreferrer"
            onClick={e => e.stopPropagation()}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              padding: '7px 13px',
              borderRadius: 10,
              fontSize: 12,
              background: isCareerPage ? '#059669' : '#fff',
              border: isCareerPage ? 'none' : '1px solid rgba(124,58,237,0.22)',
              color: isCareerPage ? '#fff' : '#7c3aed',
              textDecoration: 'none',
              fontWeight: 700,
              transition: 'all 0.15s ease'
            }}
          >
            <i className={isCareerPage ? "ti ti-external-link" : "ti ti-link"} style={{ fontSize: 13 }} />
            {isCareerPage ? "Official Apply ↗" : "View Job"}
          </a>
        )}

        <button
          onClick={e => {
            e.stopPropagation();
            handleSave();
          }}
          disabled={saving || saved}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 5,
            padding: '7px 13px',
            borderRadius: 10,
            fontSize: 12,
            background: saved ? '#d1fae5' : isCareerPage ? 'rgba(124,58,237,0.1)' : '#7c3aed',
            border: isCareerPage ? '1px solid rgba(124,58,237,0.2)' : 'none',
            color: saved ? '#065f46' : isCareerPage ? '#7c3aed' : '#fff',
            fontWeight: 700,
            cursor: saved ? 'default' : 'pointer',
            fontFamily: 'inherit',
            transition: 'all 0.15s ease'
          }}
        >
          {saved
            ? <><i className="ti ti-check" style={{ fontSize: 13 }} /> Saved</>
            : saving
              ? <><i className="ti ti-loader" style={{ fontSize: 13 }} /> Saving...</>
              : <><i className="ti ti-bookmark" style={{ fontSize: 13 }} /> Save to Tracker</>
          }
        </button>
      </div>
    </div>
  );
}

function AutoSuggestInput({ value, onChange, placeholder, type, onEnter, style }) {
  const [focused, setFocused] = useState(false)
  const [suggestions, setSuggestions] = useState([])
  const [loading, setLoading] = useState(false)
  const debouncedValue = useDebounce(value, 220)

  useEffect(() => {
    if (!focused || !debouncedValue.trim() || debouncedValue.trim().length < 2) {
      setSuggestions([])
      return
    }

    const controller = new AbortController()

    const fetchSuggestions = async () => {
      setLoading(true)
      try {
        const endpoint =
          type === 'location'
            ? `/api/suggestions/locations?q=${encodeURIComponent(debouncedValue)}`
            : `/api/suggestions/keywords?q=${encodeURIComponent(debouncedValue)}`

        const res = await api.get(endpoint, { signal: controller.signal })
        setSuggestions(Array.isArray(res.data) ? res.data : [])
      } catch (e) {
        if (e.name !== 'CanceledError') console.error(e)
      } finally {
        setLoading(false)
      }
    }

    fetchSuggestions()
    return () => controller.abort()
  }, [debouncedValue, focused, type])

  const chooseSuggestion = (item) => {
    onChange(item)
    setFocused(false)
    setSuggestions([])
  }

  return (
    <div style={{ position: 'relative', flex: 1, ...style }}>
      <input
        className="search-input"
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 140)}
        onKeyDown={e => {
          if (e.key === 'Enter') onEnter()
        }}
        style={{ width: '100%' }}
      />

      {focused && (suggestions.length > 0 || loading) && (
        <div className="suggest-box">
          {loading && (
            <div className="suggest-item muted">
              <i className="ti ti-loader" /> Searching...
            </div>
          )}

          {!loading && suggestions.map(item => (
            <div
              key={item}
              className="suggest-item"
              onMouseDown={() => chooseSuggestion(item)}
            >
              <i className={`ti ${type === 'location' ? 'ti-map-pin' : 'ti-search'}`} />
              <span>{item}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default function DiscoverPage() {
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [location, setLocation] = useState("");
  const [mode, setMode] = useState("recommended"); // recommended | search
  const [minScoreFilter, setMinScoreFilter] = useState("ALL"); // ALL | 80 | 60
  const [sourceFilter, setSourceFilter] = useState("ALL"); // ALL | CAREER_PAGE_ATS | SEARCH_ENGINE | API_AGGREGATOR
  const [error, setError] = useState("");
  const [profile, setProfile] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchRecommended();
    fetchProfile();
  }, []);

  const fetchProfile = async () => {
    try {
      const res = await api.get('/api/users/profile');
      if (res.data) {
        setProfile(res.data);
        if (res.data.preferredLocations && !location) {
          setLocation(res.data.preferredLocations.split(/[,;/]/)[0].trim());
        }
      }
    } catch (e) {
      console.error(e);
    }
  };

  const fetchRecommended = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await api.get('/api/jobs/discover/recommended');
      setJobs(res.data || []);
      setMode("recommended");
    } catch (e) {
      setError("Could not load recommendations. Check your profile settings.");
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    if (!keyword.trim()) return;
    setSearching(true);
    setError("");
    try {
      const params = {
        keyword,
        location: location || "India",
        minScore: minScoreFilter !== "ALL" ? minScoreFilter : undefined,
        sourceType: sourceFilter !== "ALL" ? sourceFilter : undefined,
      };
      const res = await api.get('/api/jobs/discover/search', { params });
      setJobs(res.data || []);
      setMode("search");
    } catch (e) {
      setError("Search failed. Try again.");
    } finally {
      setSearching(false);
    }
  };

  const handleDismiss = (jobId) => {
    setJobs(prev => prev.filter(j => j.id !== jobId));
  };

  // Local client-side filtering for immediate snappy UI response
  const filteredJobs = jobs.filter(job => {
    if (minScoreFilter === "80" && (job.matchScore || 0) < 80) return false;
    if (minScoreFilter === "60" && (job.matchScore || 0) < 60) return false;

    if (sourceFilter === "CAREER_PAGE_ATS") {
      if (job.source !== "CAREER_PAGE" && job.sourceType !== "CAREER_PAGE_ATS") return false;
    } else if (sourceFilter === "SEARCH_ENGINE") {
      if (job.source !== "GOOGLE_SEARCH" && job.sourceType !== "SEARCH_ENGINE") return false;
    } else if (sourceFilter === "API_AGGREGATOR") {
      if (job.source !== "ADZUNA" && job.source !== "JSEARCH" && job.sourceType !== "API_AGGREGATOR") return false;
    }
    return true;
  });

  return (
    <>
      <style>{`
        .discover-page { max-width: 1040px; }
        .search-bar {
          display: flex; gap: 0.75rem; margin-bottom: 1.25rem;
          background: rgba(255,255,255,0.85); backdrop-filter: blur(10px);
          padding: 0.9rem 1rem; border-radius: 18px;
          border: 1px solid rgba(255,255,255,0.6);
          box-shadow: 0 8px 24px rgba(124,58,237,0.08);
          flex-wrap: wrap; position: sticky; top: 8px; z-index: 20;
        }
        .search-input {
          flex: 1; min-width: 180px; padding: 0.55rem 0.85rem;
          border: 1.5px solid #e4daff; border-radius: 10px;
          font-size: 0.9rem; font-family: inherit; outline: none;
          color: #1a1040; transition: border-color 0.15s;
        }
        .search-input:focus { border-color: #7c3aed; }
        .jobs-grid {
          display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem;
        }
        @media (max-width: 1024px) {
          .jobs-grid { grid-template-columns: 1fr; }
        }
        .filter-chip {
          padding: 5px 12px; border-radius: 99px; font-size: 12px;
          font-weight: 600; cursor: pointer; border: 1px solid #e2e8f0;
          background: #fff; color: #64748b; transition: all 0.15s ease;
        }
        .filter-chip.active {
          background: #7c3aed; color: #fff; border-color: #7c3aed;
        }
        .btn-primary {
          display: inline-flex; align-items: center; gap: 0.4rem;
          padding: 0.55rem 1.25rem; background: #7c3aed; color: #fff;
          border: none; border-radius: 10px; font-size: 0.88rem;
          font-family: inherit; cursor: pointer; font-weight: 600;
          transition: background 0.15s; white-space: nowrap;
        }
        .btn-primary:hover { background: #6b2fd6; }
        .btn-primary:disabled { opacity: 0.65; cursor: not-allowed; }
        .btn-ghost {
          display: inline-flex; align-items: center; gap: 0.4rem;
          padding: 0.55rem 1rem; background: transparent; color: #7c6faa;
          border: 1px solid #e0d9ff; border-radius: 10px; font-size: 0.88rem;
          font-family: inherit; cursor: pointer; transition: all 0.15s; white-space: nowrap; font-weight: 600;
        }
        .btn-ghost:hover { background: #f5f0ff; color: #1a1040; }
        .empty-state { text-align: center; padding: 3rem 1rem; }
        .empty-icon { font-size: 3rem; color: #d4c9f5; display: block; margin-bottom: 1rem; }
        .suggest-box {
          position: absolute; top: calc(100% + 8px); left: 0; right: 0;
          background: #fff; border: 1px solid #e4daff; border-radius: 12px;
          box-shadow: 0 18px 40px rgba(124,58,237,0.14); overflow: hidden; z-index: 50;
        }
        .suggest-item {
          display: flex; align-items: center; gap: 9px; padding: 10px 12px;
          font-size: 13px; color: #1a1040; cursor: pointer; transition: background 0.12s ease;
        }
        .suggest-item:hover { background: #f5f0ff; color: #7c3aed; }
        .suggest-item i { font-size: 14px; color: #a78bfa; }
        .suggest-item.muted { color: #a094c4; cursor: default; }
      `}</style>

      <div className="discover-page">
        <PageHeader
          title="AI Job Discovery Engine"
          subtitle="Discover verified openings from official company career portals, Google search, and talent networks"
          icon="ti-compass"
        />

        {/* Search Bar */}
        <div className="search-bar">
          <AutoSuggestInput
            value={keyword}
            onChange={setKeyword}
            placeholder="Search role, skills, target company..."
            type="keyword"
            onEnter={handleSearch}
          />

          <AutoSuggestInput
            value={location}
            onChange={setLocation}
            placeholder="Location or Remote"
            type="location"
            onEnter={handleSearch}
            style={{ maxWidth: 220 }}
          />

          <button className="btn-primary" onClick={handleSearch} disabled={searching || !keyword.trim()}>
            {searching ? <><i className="ti ti-loader" /> Searching...</> : <><i className="ti ti-search" /> Discover</>}
          </button>

          <button className="btn-ghost" onClick={fetchRecommended} disabled={loading}>
            <i className="ti ti-sparkles" /> Recommended Fit
          </button>
        </div>

        {/* Filter Controls Bar */}
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center', marginBottom: '1.25rem' }}>
          <span style={{ fontSize: 12, fontWeight: 700, color: '#7c6faa' }}>Match Fit:</span>
          {['ALL', '80', '60'].map(score => (
            <span
              key={score}
              className={`filter-chip ${minScoreFilter === score ? 'active' : ''}`}
              onClick={() => setMinScoreFilter(score)}
            >
              {score === 'ALL' ? 'All Fits' : score === '80' ? '🎯 Strong Fit (80%+)' : '⚡ Good Fit (60%+)'}
            </span>
          ))}

          <div style={{ height: 16, width: 1, background: '#e2e8f0', margin: '0 4px' }} />

          <span style={{ fontSize: 12, fontWeight: 700, color: '#7c6faa' }}>Source:</span>
          {[
            { id: 'ALL', label: 'All Sources' },
            { id: 'CAREER_PAGE_ATS', label: '🏢 Official Career Pages' },
            { id: 'SEARCH_ENGINE', label: '🔍 Google Search' },
            { id: 'API_AGGREGATOR', label: '📊 Aggregators' }
          ].map(s => (
            <span
              key={s.id}
              className={`filter-chip ${sourceFilter === s.id ? 'active' : ''}`}
              onClick={() => setSourceFilter(s.id)}
            >
              {s.label}
            </span>
          ))}
        </div>

        {error && (
          <div style={{ background: '#fff1f1', border: '1px solid #fecaca', borderRadius: 10, padding: '10px 14px', fontSize: '0.85rem', color: '#ef4444', marginBottom: '1rem' }}>
            <i className="ti ti-alert-circle" /> {error}
          </div>
        )}

        {/* Count summary bar */}
        {!loading && filteredJobs.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: 8 }}>
            <span style={{ fontSize: '0.88rem', color: '#7c6faa' }}>
              Showing <strong>{filteredJobs.length}</strong> verified opportunities tailored for{" "}
              <strong style={{ color: "#7c3aed" }}>
                {profile?.targetRole || profile?.currentRole || "your profile"}
              </strong>
            </span>
            <span
              onClick={() => navigate("/profile")}
              style={{ fontSize: 12, color: "#7c3aed", fontWeight: 600, cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 4 }}
            >
              <i className="ti ti-adjustments" /> Adjust Career Profile
            </span>
          </div>
        )}

        {/* Grid or Skeleton */}
        {loading ? (
          <DiscoverSkeleton />
        ) : filteredJobs.length === 0 ? (
          <div className="empty-state">
            <i className="ti ti-compass-off empty-icon" />
            <p style={{ fontSize: '1.1rem', fontWeight: 600, color: '#1a1040', margin: '0 0 0.5rem' }}>
              No matching jobs found
            </p>
            <p style={{ fontSize: '0.88rem', color: '#a094c4', margin: '0 0 1.5rem' }}>
              Try loosening your match score filter or adjusting your search keywords.
            </p>
            <button className="btn-primary" onClick={() => { setMinScoreFilter('ALL'); setSourceFilter('ALL'); fetchRecommended(); }}>
              Reset Filters
            </button>
          </div>
        ) : (
          <div className="jobs-grid">
            {filteredJobs.map(job => (
              <JobCard
                key={job.id}
                job={job}
                onDismiss={handleDismiss}
              />
            ))}
          </div>
        )}
      </div>
    </>
  );
}