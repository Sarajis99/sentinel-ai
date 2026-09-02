import { useState, useEffect, useCallback, useMemo } from 'react';
import { 
  BarChart2, 
  AlertCircle, 
  Settings, 
  Activity, 
  Server, 
  Database,
  CheckCircle2,
  XCircle,
  Clock,
  ShieldAlert,
  Play,
  Square,
  Search,
  Menu,
  ChevronLeft,
  Sun,
  Moon,
  Edit3
} from 'lucide-react';
import { 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';
import './index.css';
import { api, pingAPI } from './api';
import BootScreen from './BootScreen';

const POLL_INTERVAL = 10000;

function formatTime(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' + formatTime(dateStr);
}

function formatMTTR(seconds) {
  if (!seconds) return '—';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
  return `${Math.round(seconds / 3600)}h ${Math.round((seconds % 3600) / 60)}m`;
}

function getServiceClass(name) {
  if (!name) return '';
  const key = name.split('-')[0];
  return `service-${key}`;
}

function timeAgo(dateStr) {
  if (!dateStr) return '';
  const now = new Date();
  const d = new Date(dateStr);
  const diff = Math.floor((now - d) / 1000);
  if (diff < 60) return 'just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}

export default function App() {
  const [currentView, setCurrentView] = useState('dashboard');
  const [booting, setBooting] = useState(true);
  const [incidents, setIncidents] = useState([]);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const [activeTab, setActiveTab] = useState('details');
  const [stats, setStats] = useState(null);
  const [health, setHealth] = useState(null);
  const [toasts, setToasts] = useState([]);
  const [simRunning, setSimRunning] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [modalForm, setModalForm] = useState({
    rootCause: '', rcaSummary: '', impactAnalysis: '', suggestedFix: '', prevention: ''
  });
  const [loading, setLoading] = useState(true);
  const [comments, setComments] = useState([]);
  const [newComment, setNewComment] = useState('');
  const [retrying, setRetrying] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem('sentinel-theme') || 'light');
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  // Chaos panel state
  const [chaosTarget, setChaosTarget] = useState('payment-service');
  const [chaosServices, setChaosServices] = useState(['payment-service', 'order-service', 'inventory-service', 'notification-service', 'user-service']);
  const [chaosScenarios, setChaosScenarios] = useState([]);
  const [injecting, setInjecting] = useState(null);

  // Settings state
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [apiKeyMasked, setApiKeyMasked] = useState('');
  const [llmTestResult, setLlmTestResult] = useState(null);
  const [logsPerSecond, setLogsPerSecond] = useState(5);
  const [resetting, setResetting] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  // Dark/Light mode persistence
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('sentinel-theme', theme);
  }, [theme]);

  const toggleTheme = () => setTheme(prev => prev === 'light' ? 'dark' : 'light');

  const addToast = useCallback((message, type = 'info') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
  }, []);

  const loadData = useCallback(async () => {
    try {
      const [incidentData, statsData, healthData, simStatus] = await Promise.all([
        api.getIncidents(page, 50),
        api.getStats(),
        api.getHealth(),
        api.getSimulationStatus(),
      ]);
      setIncidents(incidentData.content || []);
      setStats(statsData);
      setHealth(healthData);
      setSimRunning(simStatus.active);
      setLoading(false);
    } catch (err) {
      console.error('Failed to load data:', err);
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    if (booting) {
      const checkInitial = async () => {
        const isUp = await pingAPI();
        if (isUp) {
          setBooting(false);
        }
      };
      checkInitial();
      return;
    }

    loadData();
    const interval = setInterval(loadData, POLL_INTERVAL);
    return () => clearInterval(interval);
  }, [loadData, booting]);

  const trendData = useMemo(() => {
    let raw = stats?.dailyTrend || [];
    const result = [];
    const today = new Date();
    
    // Create an array of the last 7 days (including today)
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const dateStr = d.toLocaleDateString('en-US', { month: 'short', day: '2-digit' });
      
      // Look for this date in the raw data
      const existing = raw.find(item => item.date === dateStr);
      result.push({
        date: dateStr,
        incidents: existing ? Number(existing.incidents) : 0
      });
    }
    
    return result;
  }, [stats?.dailyTrend]);

  // ─── Manual Disposition ────────────────────────────────────────────────
  const handleOpenNewModal = () => {
    setModalForm({ rootCause: '', rcaSummary: '', impactAnalysis: '', suggestedFix: '', prevention: '' });
    setShowModal(true);
  };

  const handleOpenEditModal = () => {
    if (selected) {
      setModalForm({
        rootCause: selected.rootCause || '',
        rcaSummary: selected.rcaSummary || '',
        impactAnalysis: selected.impactAnalysis || '',
        suggestedFix: selected.suggestedFix || '',
        prevention: selected.prevention || ''
      });
      setShowModal(true);
    }
  };

  // ─── Action Handlers ──────────────────────────────────────────────────
  const handleSimulate = async () => {
    try {
      const res = await api.triggerSimulation();
      addToast(res.message, res.status === 'started' ? 'success' : 'warning');
      setSimRunning(res.status === 'started');
      setTimeout(loadData, 2000);
    } catch (err) {
      addToast(err.message, 'error');
    }
  };

  const handleStopSimulation = async () => {
    try {
      const res = await api.stopSimulation();
      addToast(res.message || 'Simulation stopped', 'info');
      setSimRunning(false);
      setTimeout(loadData, 1000);
    } catch (err) {
      addToast(err.message, 'error');
    }
  };

  const handleInjectAnomaly = async (scenarioId) => {
    if (!simRunning) {
      addToast('Start the simulation engine first!', 'warning');
      return;
    }
    try {
      setInjecting(scenarioId);
      const res = await api.injectAnomaly(scenarioId, chaosTarget);
      addToast(res.message || `🚨 Injected ${scenarioId} on ${chaosTarget}`, 'success');
      setTimeout(loadData, 3000);
    } catch (err) {
      addToast(err.message, 'error');
    } finally {
      setInjecting(null);
    }
  };

  // Load chaos scenarios on mount
  useEffect(() => {
    const loadChaos = async () => {
      try {
        const [scenarios, services] = await Promise.all([
          api.getChaosScenarios(),
          api.getChaosServices(),
        ]);
        if (scenarios?.length) setChaosScenarios(scenarios);
        if (services?.length) setChaosServices(services);
      } catch { /* use defaults */ }
    };
    loadChaos();
  }, []);

  // Load settings on mount
  useEffect(() => {
    const loadSettings = async () => {
      try {
        const [keyData, simConfig] = await Promise.all([
          api.getApiKey(),
          api.getSimConfig(),
        ]);
        if (keyData?.maskedKey) setApiKeyMasked(keyData.maskedKey);
        if (simConfig?.logsPerSecond) setLogsPerSecond(simConfig.logsPerSecond);
      } catch { /* use defaults */ }
    };
    loadSettings();
  }, []);

  const handleSaveApiKey = async () => {
    if (!apiKeyInput.trim()) return;
    try {
      const res = await api.updateApiKey(apiKeyInput.trim());
      setApiKeyMasked(res.maskedKey || '••••••••');
      setApiKeyInput('');
      addToast('API key saved securely', 'success');
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleClearApiKey = async () => {
    try {
      await api.updateApiKey("");
      setApiKeyMasked("");
      setApiKeyInput("");
      addToast("API key cleared", "success");
    } catch (err) {
      addToast(err.message, "error");
    }
  };

  const handleTestLLM = async () => {
    try {
      setLlmTestResult(null);
      const res = await api.testLLM();
      setLlmTestResult(res);
    } catch (err) { setLlmTestResult({ success: false, message: err.message }); }
  };

  const handleFactoryReset = async () => {
    try {
      setResetting(true);
      await api.factoryReset();
      addToast('🗑️ All data cleared successfully!', 'success');
      setShowResetConfirm(false);
      loadData();
    } catch (err) { addToast(err.message, 'error'); }
    finally { setResetting(false); }
  };

  const handleUpdateLogsPerSecond = async (val) => {
    setLogsPerSecond(val);
    try {
      await api.updateSimConfig({ logsPerSecond: val });
    } catch { /* silent */ }
  };

  const handleResolve = async (id) => {
    try {
      const updated = await api.resolveIncident(id);
      addToast(`Incident resolved! MTTR: ${formatMTTR(updated.mttrSeconds)}`, 'success');
      setSelected(updated);
      loadData();
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleDismiss = async (id) => {
    try {
      const updated = await api.dismissIncident(id);
      addToast('Incident dismissed as false positive', 'info');
      setSelected(updated);
      loadData();
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleManualDisposition = async () => {
    if (!selected) return;
    if (!modalForm.rootCause || !modalForm.rcaSummary || !modalForm.impactAnalysis || !modalForm.suggestedFix || !modalForm.prevention) {
      addToast('Please fill all mandatory fields to submit the manual triage report.', 'error');
      return;
    }
    try {
      const updated = await api.manualDisposition(selected.incidentId, modalForm);
      addToast('Manual disposition applied successfully', 'success');
      setSelected(updated);
      setShowModal(false);
      setModalForm({ rootCause: '', rcaSummary: '', impactAnalysis: '', suggestedFix: '', prevention: '' });
      loadData();
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleAccept = async (id) => {
    try {
      const updated = await api.acceptIncident(id);
      addToast('Incident accepted — status: In Progress', 'success');
      setSelected(updated);
      loadData();
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleClose = async (id) => {
    try {
      const updated = await api.closeIncident(id);
      addToast('Incident closed', 'success');
      setSelected(updated);
      loadData();
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleRetryAnalysis = async (id) => {
    try {
      setRetrying(true);
      await api.retryAnalysis(id);
      addToast('AI analysis retry started...', 'info');
      
      // Optimistically update UI to show spinner
      if (selected && selected.incidentId === id) {
        setSelected(prev => ({ ...prev, status: 'ASSESSING' }));
      }

      let attempts = 0;
      let wasAssessing = false;
      
      const pollInterval = setInterval(async () => {
        attempts++;
        try {
          const updated = await api.getIncident(id);
          
          if (updated.status === 'ASSESSING') {
            wasAssessing = true;
            setSelected(updated);
          } else if (updated.status === 'RCA_COMPLETE' || (wasAssessing && updated.status === 'AWAITING_TRIAGE') || attempts > 15) {
            clearInterval(pollInterval);
            setSelected(updated);
            loadData();
            setRetrying(false);
            
            if (updated.status === 'RCA_COMPLETE') {
              addToast('AI analysis complete!', 'success');
            } else if (updated.status === 'AWAITING_TRIAGE') {
              addToast('AI analysis failed again.', 'error');
            }
          }
        } catch (e) { /* ignore */ }
      }, 2000);
    } catch (err) {
      addToast(err.message, 'error');
      setRetrying(false);
    }
  };

  const loadComments = async (incidentId) => {
    try {
      const data = await api.getComments(incidentId);
      setComments(data);
    } catch { setComments([]); }
  };

  const handleAddComment = async () => {
    if (!selected || !newComment.trim()) return;
    try {
      await api.addComment(selected.incidentId, { author: 'Analyst', content: newComment.trim() });
      setNewComment('');
      loadComments(selected.incidentId);
      addToast('Work note added', 'success');
    } catch (err) { addToast(err.message, 'error'); }
  };

  const handleSelectIncident = async (inc) => {
    try {
      const full = await api.getIncident(inc.incidentId);
      setSelected(full);
      setActiveTab('details');
      loadComments(full.incidentId);
    } catch {
      setSelected(inc);
      setComments([]);
    }
  };

  // ─── Sub-Components ───────────────────────────────────────────────────
  const renderDashboardView = () => (
    <>
      <div className="metrics-grid">
        <div className="metric-card">
          <div className="metric-card__header">
            <span className="metric-card__label">Total Incidents</span>
            <Activity className="metric-card__icon" size={16} />
          </div>
          <div className="metric-card__value">{stats?.totalIncidents ?? '—'}</div>
          <div className="metric-card__sub">{stats?.totalAnomalies ?? 0} anomalies detected</div>
        </div>
        <div className="metric-card" style={{borderTop: '3px solid var(--severity-p1)'}}>
          <div className="metric-card__header">
            <span className="metric-card__label">Active Incidents</span>
            <ShieldAlert className="metric-card__icon" size={16} color="var(--severity-p1)" />
          </div>
          <div className="metric-card__value" style={{color: 'var(--severity-p1)'}}>{stats ? (stats.awaitingTriageCount + stats.inProgressCount) : '—'}</div>
          <div className="metric-card__sub">{stats?.awaitingTriageCount ?? 0} awaiting triage</div>
        </div>
        <div className="metric-card" style={{borderTop: '3px solid var(--accent-emerald)'}}>
          <div className="metric-card__header">
            <span className="metric-card__label">Resolved</span>
            <CheckCircle2 className="metric-card__icon" size={16} color="var(--accent-emerald)" />
          </div>
          <div className="metric-card__value" style={{color: 'var(--accent-emerald)'}}>{stats ? (stats.resolvedIncidents + stats.closedCount) : '—'}</div>
          <div className="metric-card__sub">Avg MTTR: {formatMTTR(stats?.averageMttrSeconds)}</div>
        </div>
        <div className="metric-card">
          <div className="metric-card__header">
            <span className="metric-card__label">Log Events</span>
            <Database className="metric-card__icon" size={16} />
          </div>
          <div className="metric-card__value">{stats?.totalLogEvents?.toLocaleString() ?? '—'}</div>
          <div className="metric-card__sub">Processed in last 24h</div>
        </div>
      </div>

      {/* ─── Chaos Engineering Panel ───────────────────────────────── */}
      <div className="chaos-panel">
        <div className="chaos-panel__header">
          <span className="chaos-panel__title">🔥 Chaos Engineering Lab</span>
          <div className="chaos-panel__controls">
            <label style={{fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600}}>Target:</label>
            <select className="chaos-panel__service-select" value={chaosTarget} onChange={e => setChaosTarget(e.target.value)}>
              {chaosServices.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
        </div>
        {simRunning ? (
          <div className="chaos-grid">
            {(chaosScenarios.length > 0 ? chaosScenarios : [
              { id: 'ERROR_SPIKE', label: 'Error Spike', icon: '⚡' },
              { id: 'LATENCY_SURGE', label: 'Latency Surge', icon: '🐢' },
              { id: 'DB_OUTAGE', label: 'DB Outage', icon: '💥' },
              { id: 'MEMORY_LEAK', label: 'Memory Leak', icon: '💧' },
              { id: 'DOWNSTREAM_FAILURE', label: 'Downstream Timeout', icon: '🔗' },
              { id: 'RATE_LIMIT_SPIKE', label: 'Rate Limit', icon: '🛑' },
              { id: 'CONFIG_ERROR', label: 'Bad Config', icon: '📝' },
            ]).map(s => (
              <button
                key={s.id}
                className="chaos-btn"
                onClick={() => handleInjectAnomaly(s.id)}
                disabled={injecting === s.id}
              >
                <span className="chaos-btn__icon">{s.icon}</span>
                {injecting === s.id ? 'Injecting...' : s.label}
              </button>
            ))}
          </div>
        ) : (
          <div className="chaos-panel__disabled-msg">
            ⚠️ Start the simulation engine to unlock anomaly injection
          </div>
        )}
      </div>

      <div className="dashboard-grid">
        {/* Trend Graph */}
        <div className="dashboard-panel">
          <div className="panel-header">
            <span className="panel-title"><BarChart2 size={16} /> Anomaly Detection Trend (Last 7 Days)</span>
          </div>
          <div className="panel-content" style={{height: '300px'}}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trendData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorEvents" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="var(--accent-primary)" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="var(--accent-primary)" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="var(--text-muted)" fontSize={12} tickLine={false} axisLine={false} />
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border-subtle)" />
                <Tooltip 
                  contentStyle={{ backgroundColor: 'var(--bg-card)', border: '1px solid var(--border-medium)', borderRadius: '4px' }}
                  itemStyle={{ color: 'var(--text-primary)', fontWeight: 600 }}
                />
                <Area type="monotone" dataKey="incidents" stroke="var(--accent-primary)" strokeWidth={2} fillOpacity={1} fill="url(#colorEvents)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Health Grid */}
        <div className="dashboard-panel">
          <div className="panel-header">
            <span className="panel-title"><Server size={16} /> System Health Overview</span>
          </div>
          <div className="panel-content">
            <div className="health-grid">
              {['payment-service', 'order-service', 'inventory-service', 'notification-service', 'user-service'].map(svc => {
                const sHealth = health?.services?.[svc];
                const status = sHealth?.status || 'UNKNOWN';
                return (
                  <div className="health-item" key={svc}>
                    <span className="health-item__name"><Server size={14} /> {svc.split('-').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')}</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      {sHealth && <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>{Math.round(sHealth.errorRate * 100)}% err | p99: {Math.round(sHealth.p99Latency || 0)}ms</span>}
                      <span className={`health-indicator ${status.toLowerCase()}`}>{status}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      {/* Recent Activity Feed */}
      <div className="dashboard-panel" style={{marginBottom: 0}}>
        <div className="panel-header">
          <span className="panel-title"><Clock size={16} /> Recent Activity Feed</span>
          <button className="btn" onClick={() => setCurrentView('incidents')} style={{padding: '4px 8px', fontSize: '0.75rem'}}>View All Incidents</button>
        </div>
        <div className="panel-content no-pad">
          {incidents.slice(0, 5).map(inc => (
            <div key={inc.incidentId} className="feed-item" onClick={() => { setCurrentView('incidents'); handleSelectIncident(inc); }}>
              <div className="feed-item__header">
                <span className="feed-item__title">
                  <span className="incident-number-inline">{inc.incidentNumber || 'INC-NEW'}</span> 
                  {inc.title || 'Untitled Incident'}
                </span>
                <span className={`badge badge-${inc.severity?.toLowerCase()}`}>{inc.severity}</span>
              </div>
              <div className="feed-item__meta">
                <span className={`badge badge-${inc.status?.toLowerCase()?.replace('_', '-')}`}>{inc.status}</span>
                <span className={getServiceClass(inc.serviceName)}>{inc.serviceName}</span>
                <span className="feed-item__time">{formatDate(inc.detectedAt)}</span>
              </div>
            </div>
          ))}
          {incidents.length === 0 && <div className="empty-state" style={{padding: '24px'}}>No recent activity.</div>}
        </div>
      </div>
    </>
  );

  const renderIncidentsView = () => (
    <div className="incident-view">
      {/* Left: Incident List */}
      <div className={`incident-list-panel ${selected ? 'mobile-hide' : ''}`}>
        <div className="panel-header">
          <span className="panel-title">Active Queue</span>
          <span className="badge" style={{background: 'var(--bg-primary)'}}>{incidents.length} loaded</span>
        </div>
        <div className="feed-list">
          {loading && <div className="empty-state"><div className="spinner" />Loading...</div>}
          {!loading && incidents.length === 0 && (
            <div className="empty-state">
              <div className="empty-state__icon">🔍</div>
              <div>No incidents in queue.</div>
            </div>
          )}
          {incidents.map(inc => (
            <div
              key={inc.incidentId}
              className={`feed-item ${selected?.incidentId === inc.incidentId ? 'active' : ''} ${inc.rootCause === 'UNKNOWN' ? 'unknown' : ''}`}
              onClick={() => handleSelectIncident(inc)}
            >
              <div className="feed-item__header">
                <span className="feed-item__title">
                  <span className="incident-number-inline">{inc.incidentNumber || 'INC-NEW'}</span>
                  {inc.title || 'Untitled Incident'}
                </span>
                <span className={`badge badge-${inc.severity?.toLowerCase()}`}>{inc.severity}</span>
              </div>
              <div className="feed-item__meta">
                <span className={`badge badge-${inc.status?.toLowerCase()?.replace('_', '-')}`}>{inc.status}</span>
                <span className={getServiceClass(inc.serviceName)}>{inc.serviceName}</span>
                <span className="feed-item__time">{formatTime(inc.detectedAt)}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Right: Detail Panel */}
      <div className={`incident-detail-panel ${!selected ? 'mobile-hide' : ''}`} style={{display: 'flex', flexDirection: 'column', overflow: 'hidden'}}>
        {!selected ? (
          <div className="empty-state">
            <div className="empty-state__icon">📋</div>
            <div>Select an incident from the queue to view RCA details.</div>
          </div>
        ) : (
          <div className="detail-panel" style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', padding: 0}}>
            <div className="detail-header" style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px', padding: '16px 24px', borderBottom: '1px solid var(--border-subtle)'}}>
              <div style={{display: 'flex', alignItems: 'center', gap: '8px'}}>
                <button className="mobile-back-btn" onClick={() => setSelected(null)}>
                  <ChevronLeft size={16} /> Back
                </button>
                <div className="detail-header__top" style={{marginBottom: 0}}>
                  <span className="incident-number-badge">{selected.incidentNumber || 'INC-NEW'}</span>
                  <h2 className="detail-header__title" style={{marginBottom: 0, display: 'inline-block'}}>{selected.title}</h2>
                </div>
              </div>
              
              <div className="action-bar" style={{borderTop: 'none', padding: 0, background: 'transparent'}}>
                {(selected.status === 'NEW' || selected.status === 'ASSESSING') && (
                  <span className="ai-processing-indicator">
                    <span className="spinner" style={{width: '14px', height: '14px'}} /> AI is analyzing this incident...
                  </span>
                )}
                {selected.status === 'AWAITING_TRIAGE' && (
                  <>
                    <button className="btn btn-primary" onClick={() => handleRetryAnalysis(selected.incidentId)} disabled={retrying}>
                      {retrying ? <><span className="spinner" style={{width: '14px', height: '14px'}} /> Retrying...</> : '🔄 Retry AI Analysis'}
                    </button>
                    <button className="btn btn-warning" onClick={handleOpenNewModal}>
                      <ShieldAlert size={14} /> Manual Triage
                    </button>
                    <button className="btn" onClick={() => handleDismiss(selected.incidentId)}>
                      <XCircle size={14} /> Dismiss
                    </button>
                  </>
                )}
                {selected.status === 'RCA_COMPLETE' && (
                  <>
                    <button className="btn btn-success" onClick={() => handleAccept(selected.incidentId)}>
                      <CheckCircle2 size={14} /> Accept & Work
                    </button>
                    <button className="btn" onClick={() => handleDismiss(selected.incidentId)}>
                      <XCircle size={14} /> Dismiss
                    </button>
                  </>
                )}
                {selected.status === 'IN_PROGRESS' && (
                  <>
                    <button className="btn btn-success" onClick={() => handleResolve(selected.incidentId)}>
                      <CheckCircle2 size={14} /> Resolve
                    </button>
                    <button className="btn" onClick={() => handleDismiss(selected.incidentId)}>
                      <XCircle size={14} /> Dismiss
                    </button>
                  </>
                )}
                {selected.status === 'RESOLVED' && (
                  <button className="btn btn-primary" onClick={() => handleClose(selected.incidentId)}>
                    🔒 Close Incident
                  </button>
                )}
                {selected.confidence == null && selected.status !== 'CLOSED' && selected.status !== 'AWAITING_TRIAGE' && (
                  <button className="btn btn-warning" onClick={handleOpenEditModal}>
                    <Edit3 size={14} /> Edit Triage
                  </button>
                )}
              </div>
            </div>

            {/* Top Level Tabs */}
            <div className="tabs">
              <button className={`tab ${activeTab === 'details' ? 'active' : ''}`} onClick={() => setActiveTab('details')}>
                Incident Details
              </button>
              <button className={`tab ${activeTab === 'analysis' ? 'active' : ''}`} onClick={() => setActiveTab('analysis')}>
                {selected.confidence == null && ['RCA_COMPLETE', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].includes(selected.status) ? '🧠 Manual Analysis' : '✨ AI Analysis'}
              </button>
              <button className={`tab ${activeTab === 'raw' ? 'active' : ''}`} onClick={() => setActiveTab('raw')}>
                Raw Logs
              </button>
            </div>

            {/* Tab Content */}
            <div className="detail-content">
              {activeTab === 'details' && (
                <>
                  {selected.status === 'AWAITING_TRIAGE' && (
                    <div className="ai-status-banner warning">
                      ⚠️ AI analysis was unavailable for this incident. Use "Retry AI Analysis" or submit a manual triage report.
                    </div>
                  )}
                  <div className="sn-form-container">
                  {/* ServiceNow Header */}
                  <div className="sn-header-bar">
                    <div className="sn-header-left">
                      <button className="sn-icon-btn-borderless"><ChevronLeft size={18} /></button>
                      <button className="sn-icon-btn-borderless"><Menu size={18} /></button>
                      <span className="sn-header-title">Incident {selected.incidentNumber || 'INC-NEW'}</span>
                    </div>
                  </div>

                  {/* Process Flow Formatter (Stepper) */}
                  <div className="sn-process-flow">
                    {['NEW', 'ASSESSING', 'RCA_COMPLETE', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].map((step, idx) => {
                      const statusOrder = ['NEW', 'ASSESSING', 'RCA_COMPLETE', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
                      const currentIdx = selected.status === 'AWAITING_TRIAGE' ? 2 : statusOrder.indexOf(selected.status);
                      const stepLabels = { NEW: 'New', ASSESSING: 'Assess', RCA_COMPLETE: 'Root Cause Analysis', IN_PROGRESS: 'Fix in Progress', RESOLVED: 'Resolved', CLOSED: 'Closed' };
                      
                      let stepClass = '';
                      if (step === 'RCA_COMPLETE' && selected.status === 'AWAITING_TRIAGE') {
                        stepClass = 'warning';
                      } else if (idx < currentIdx) {
                        stepClass = 'completed';
                      } else if (idx === currentIdx) {
                        stepClass = 'active';
                      }
                      
                      return (
                        <div key={step} className={`sn-flow-step ${stepClass}`}>
                          {stepClass === 'completed' && <span className="step-check">✓ </span>}
                          {stepLabels[step]}
                        </div>
                      );
                    })}
                  </div>

                  {/* Form Content */}
                  <div className="sn-form-body">
                    <div className="sn-form-grid">
                      {/* Left Column */}
                      <div className="sn-form-col">
                        <div className="sn-form-group">
                          <label className="sn-label">Number</label>
                          <input type="text" className="sn-input sn-readonly" value={selected.incidentNumber || 'INC-NEW'} readOnly />
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Caller</label>
                          <div className="sn-input-group">
                            <input type="text" className="sn-input" value="Sentinel Detector" readOnly />
                            <button className="sn-icon-btn"><Search size={14} /></button>
                          </div>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Category</label>
                          <select className="sn-input" defaultValue="Software">
                            <option>Software</option>
                            <option>Hardware</option>
                            <option>Network</option>
                          </select>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Subcategory</label>
                          <select className="sn-input" defaultValue="Microservice">
                            <option>Microservice</option>
                            <option>Database</option>
                            <option>Internal</option>
                          </select>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Configuration item</label>
                          <div className="sn-input-group">
                            <input type="text" className="sn-input" value={selected.serviceName} readOnly />
                            <button className="sn-icon-btn"><Search size={14} /></button>
                          </div>
                        </div>
                      </div>

                      {/* Right Column */}
                      <div className="sn-form-col">
                        <div className="sn-form-group">
                          <label className="sn-label">State</label>
                          <select className="sn-input" value={selected.status} readOnly disabled>
                            <option value="NEW">New</option>
                            <option value="ASSESSING">Assessing</option>
                            <option value="RCA_COMPLETE">RCA Complete</option>
                            <option value="AWAITING_TRIAGE">Awaiting Triage</option>
                            <option value="IN_PROGRESS">In Progress</option>
                            <option value="RESOLVED">Resolved</option>
                            <option value="CLOSED">Closed</option>
                          </select>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Impact</label>
                          <select className="sn-input" defaultValue={['P0', 'P1'].includes(selected.severity) ? '1 - High' : selected.severity === 'P2' ? '2 - Medium' : '3 - Low'}>
                            <option>1 - High</option>
                            <option>2 - Medium</option>
                            <option>3 - Low</option>
                          </select>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Urgency</label>
                          <select className="sn-input" defaultValue={['P0', 'P1'].includes(selected.severity) ? '1 - High' : selected.severity === 'P2' ? '2 - Medium' : '3 - Low'}>
                            <option>1 - High</option>
                            <option>2 - Medium</option>
                            <option>3 - Low</option>
                          </select>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Priority</label>
                          <input type="text" className="sn-input sn-readonly" value={selected.severity === 'P0' ? '1 - Critical' : selected.severity === 'P1' ? '2 - High' : selected.severity === 'P2' ? '3 - Moderate' : '4 - Low'} readOnly />
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Assignment group</label>
                          <div className="sn-input-group">
                            <input type="text" className="sn-input" value="Sentinel AI Triage" readOnly />
                            <button className="sn-icon-btn"><Search size={14} /></button>
                          </div>
                        </div>
                        <div className="sn-form-group">
                          <label className="sn-label">Assigned to</label>
                          <div className="sn-input-group">
                            <input type="text" className="sn-input" value="Auto-Remediation Bot" readOnly />
                            <button className="sn-icon-btn"><Search size={14} /></button>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Full Width Fields */}
                    <div className="sn-form-full">
                      <div className="sn-form-group">
                        <label className="sn-label required">Short description</label>
                        <input type="text" className="sn-input" value={selected.title} readOnly />
                      </div>
                      <div className="sn-form-group">
                        <label className="sn-label">Description</label>
                        <textarea className="sn-textarea" readOnly value={`Source Anomaly ID: ${selected.anomalyId}\nDetected At: ${formatDate(selected.detectedAt)}\nAnalyzed At: ${formatDate(selected.analyzedAt)}\n\nAutomated Incident created by Sentinel AI. This incident was generated due to an anomaly in ${selected.serviceName}.`} />
                      </div>
                    </div>
                  </div>
                </div>

                {/* Work Notes Section */}
                {!['NEW', 'ASSESSING'].includes(selected.status) && (
                  <div className="work-notes-section">
                    <div className="work-notes-header">
                      <span className="work-notes-title">💬 Work Notes</span>
                      <span className="work-notes-count">{comments.length} notes</span>
                    </div>
                    
                    {selected.status !== 'CLOSED' && (
                      <div className="work-notes-input-area">
                        <textarea
                          className="work-notes-textarea"
                          placeholder="Add a work note..."
                          value={newComment}
                          onChange={(e) => setNewComment(e.target.value)}
                          onKeyDown={(e) => { if (e.key === 'Enter' && e.ctrlKey) handleAddComment(); }}
                        />
                        <button className="btn btn-primary work-notes-submit" onClick={handleAddComment} disabled={!newComment.trim()}>
                          Add Note
                        </button>
                      </div>
                    )}
                    
                    <div className="work-notes-list">
                      {comments.length === 0 && (
                        <div className="work-notes-empty">No work notes yet. Add one to document your investigation.</div>
                      )}
                      {comments.map(c => (
                        <div key={c.commentId} className="work-note-item">
                          <div className="work-note-meta">
                            <span className="work-note-author">{c.author}</span>
                            <span className="work-note-time">{timeAgo(c.createdAt)}</span>
                          </div>
                          <div className="work-note-content">{c.content}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                </>
              )}

              {activeTab === 'analysis' && (
                  <div className="rca-grid" style={{marginTop: '16px'}}>
                    <div>
                      <div className="rca-section__label">Root Cause</div>
                      <div className="rca-section__text">{selected.rootCause || 'Not determined'}</div>
                    </div>
                    <div>
                      <div className="rca-section__label">Summary</div>
                      <div className="rca-section__text">{selected.rcaSummary || 'No summary available'}</div>
                    </div>
                    <div>
                      <div className="rca-section__label">Impact Analysis</div>
                      <div className="rca-section__text">{selected.impactAnalysis || 'Not assessed'}</div>
                    </div>
                    <div>
                      <div className="rca-section__label">Suggested Fix</div>
                      <div className="rca-section__text">{selected.suggestedFix || 'No fix suggested'}</div>
                    </div>
                    <div>
                      <div className="rca-section__label">Prevention</div>
                      <div className="rca-section__text">{selected.prevention || 'No prevention steps'}</div>
                    </div>
                    {selected.confidence != null && (
                      <div>
                        <div className="rca-section__label">AI Confidence</div>
                        <div className="confidence-bar">
                          <div
                            className={`confidence-bar__fill ${(selected.confidence ?? 0) >= 0.7 ? 'confidence-high' : (selected.confidence ?? 0) >= 0.4 ? 'confidence-medium' : 'confidence-low'}`}
                            style={{ width: `${(selected.confidence ?? 0) * 100}%` }}
                          />
                        </div>
                        <div style={{fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '4px', fontWeight: 600}}>
                          {((selected.confidence ?? 0) * 100).toFixed(0)}% CONFIDENCE
                        </div>
                      </div>
                    )}
                  </div>
              )}

              {activeTab === 'raw' && (
                  <div className="raw-logs" style={{marginTop: '16px'}}>
                    {(() => {
                      if (!selected.relatedLogs || selected.relatedLogs === '[]' || selected.relatedLogs.trim() === '') {
                        return 'No raw logs available for this incident.';
                      }
                      try {
                        const parsed = JSON.parse(selected.relatedLogs);
                        if (!Array.isArray(parsed)) return selected.relatedLogs;
                        return parsed.map((log, idx) => (
                          <div key={idx} style={{marginBottom: '8px', paddingBottom: '8px', borderBottom: '1px solid #e2e8f0'}}>
                            <span style={{color: '#64748b', marginRight: '8px'}}>[{log.timestamp || log.time || '—'}]</span>
                            <span style={{color: log.level === 'ERROR' ? '#ef4444' : log.level === 'WARN' ? '#f59e0b' : '#3b82f6', fontWeight: 600}}>{log.level || 'INFO'}</span>
                            <span style={{marginLeft: '8px'}}>{log.message || JSON.stringify(log)}</span>
                          </div>
                        ));
                      } catch (e) {
                        return selected.relatedLogs;
                      }
                    })()}
                  </div>
                )}
              </div>
            </div>
        )}
      </div>
    </div>
  );

  // ─── Settings View ──────────────────────────────────────────────────────
  const renderSettingsView = () => (
    <div className="settings-view">
      {/* Section 1: AI & API Configuration */}
      <div className="settings-card">
        <div className="settings-card__header">
          <Activity size={16} />
          <span className="settings-card__title">AI & API Configuration</span>
        </div>
        <div className="settings-card__body">
          <div className="settings-field">
            <span className="settings-field__label">OpenRouter API Key</span>
            {apiKeyMasked && (
              <span className="settings-badge success" style={{alignSelf: 'flex-start', marginBottom: '4px'}}>
                🔒 Current: {apiKeyMasked}
              </span>
            )}
            <div className="settings-field__row">
              <input
                type="password"
                className="settings-input"
                placeholder="sk-or-v1-..."
                value={apiKeyInput}
                onChange={e => setApiKeyInput(e.target.value)}
              />
              <button className="btn btn-primary" onClick={handleSaveApiKey} disabled={!apiKeyInput.trim()} style={{whiteSpace: 'nowrap'}}>
                Save Key
              </button>
              <button className="btn btn-danger" onClick={handleClearApiKey} disabled={!apiKeyMasked} style={{whiteSpace: 'nowrap'}}>
                Clear Key
              </button>
              <button className="btn" onClick={handleTestLLM} style={{whiteSpace: 'nowrap'}}>
                Test Connection
              </button>
            </div>
            <span className="settings-field__hint">
              Your API key is encrypted with AES-256-GCM before being stored. It is never exposed in API responses.
            </span>
            {llmTestResult && (
              <span className={`settings-badge ${llmTestResult.success ? 'success' : 'error'}`} style={{alignSelf: 'flex-start', marginTop: '4px'}}>
                {llmTestResult.success ? '✅' : '❌'} {llmTestResult.message}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Section 2: Simulation Configuration */}
      <div className="settings-card">
        <div className="settings-card__header">
          <Server size={16} />
          <span className="settings-card__title">Simulation Configuration</span>
        </div>
        <div className="settings-card__body">
          <div className="settings-field">
            <span className="settings-field__label">Log Ingestion Rate</span>
            <div className="settings-slider-container">
              <span style={{fontSize: '0.75rem', color: 'var(--text-muted)'}}>1/s</span>
              <input
                type="range"
                className="settings-slider"
                min="1"
                max="20"
                value={logsPerSecond}
                onChange={e => handleUpdateLogsPerSecond(parseInt(e.target.value))}
              />
              <span style={{fontSize: '0.75rem', color: 'var(--text-muted)'}}>20/s</span>
              <span className="settings-slider__value">{logsPerSecond} logs/s</span>
            </div>
            <span className="settings-field__hint">
              Controls how many log events per second the simulator generates. Higher values create more realistic traffic but consume more resources.
            </span>
          </div>
        </div>
      </div>

      {/* Section 3: Danger Zone */}
      <div className="settings-card danger-zone">
        <div className="settings-card__header">
          <XCircle size={16} color="var(--severity-p0)" />
          <span className="settings-card__title">Danger Zone</span>
        </div>
        <div className="settings-card__body">
          <div className="settings-field">
            <span className="settings-field__label">Factory Reset</span>
            <span className="settings-field__hint" style={{marginBottom: '8px'}}>
              This will permanently delete ALL incidents, logs, anomalies, and cached data. Kafka offsets will not be reset. This action cannot be undone.
            </span>
            {!showResetConfirm ? (
              <button className="btn-danger" onClick={() => setShowResetConfirm(true)} style={{alignSelf: 'flex-start'}}>
                🗑️ Reset All Data
              </button>
            ) : (
              <div className="settings-field__row">
                <span style={{fontSize: '0.82rem', fontWeight: 600, color: 'var(--severity-p0)'}}>
                  Are you sure? This cannot be undone.
                </span>
                <button className="btn-danger" onClick={handleFactoryReset} disabled={resetting} style={{whiteSpace: 'nowrap'}}>
                  {resetting ? 'Resetting...' : 'Yes, Delete Everything'}
                </button>
                <button className="btn" onClick={() => setShowResetConfirm(false)} style={{whiteSpace: 'nowrap'}}>
                  Cancel
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );

  // ─── Main Render ──────────────────────────────────────────────────────
  if (booting) {
    return <BootScreen onReady={() => setBooting(false)} />;
  }

  return (
    <div className="app-layout">
      {/* ─── Toasts ──────────────────────────────────────────────────── */}
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className={`toast ${t.type}`}>
            {t.type === 'success' && '✅ '}
            {t.type === 'error' && '❌ '}
            {t.type === 'warning' && '⚠️ '}
            {t.type === 'info' && 'ℹ️ '}
            {t.message}
          </div>
        ))}
      </div>

      {/* ─── Global Sidebar (Left) ────────────────────────────────────── */}
      <div 
        className={`sidebar-overlay ${isSidebarOpen ? 'open' : ''}`}
        onClick={() => setIsSidebarOpen(false)}
      ></div>
      <aside className={`sidebar ${isSidebarOpen ? 'open' : ''}`}>
        <div className="sidebar__brand">
          <ShieldAlert className="sidebar__logo-icon" size={28} />
          <div className="sidebar__logo-text">
            <div className="sidebar__logo">Sentinel AI</div>
            <div className="sidebar__subtitle">Command Center</div>
          </div>
        </div>
        
        <nav className="sidebar__nav">
          <div 
            className={`nav-item ${currentView === 'dashboard' ? 'active' : ''}`}
            onClick={() => { setCurrentView('dashboard'); setIsSidebarOpen(false); }}
          >
            <BarChart2 className="icon" /> Dashboard
          </div>
          <div 
            className={`nav-item ${currentView === 'incidents' ? 'active' : ''}`}
            onClick={() => { setCurrentView('incidents'); setIsSidebarOpen(false); }}
          >
            <AlertCircle className="icon" /> Incidents
          </div>
          <div 
            className={`nav-item ${currentView === 'settings' ? 'active' : ''}`}
            onClick={() => { setCurrentView('settings'); setIsSidebarOpen(false); }}
          >
            <Settings className="icon" /> Settings
          </div>
        </nav>

        <div className="sidebar__footer">
          {simRunning ? (
            <button
              className="btn-simulate running"
              onClick={handleStopSimulation}
              id="simulate-btn"
            >
              <Square size={16} fill="currentColor" /> Stop Simulation
            </button>
          ) : (
            <button
              className="btn-simulate"
              onClick={handleSimulate}
              id="simulate-btn"
            >
              <Play size={16} fill="currentColor" /> Start Simulation
            </button>
          )}
        </div>
      </aside>

      {/* ─── Main Workspace (Right) ────────────────────────────────────── */}
      <main className="workspace">
        
        {/* Top Header */}
        <header className="top-header">
          <button 
            className="mobile-menu-btn" 
            onClick={() => setIsSidebarOpen(true)}
            aria-label="Open Menu"
          >
            <Menu size={24} />
          </button>
          <h1 className="top-header__title">
            {currentView === 'dashboard' ? 'Global Overview' : currentView === 'settings' ? 'Settings' : 'Incident Management'}
          </h1>
          
          <button className="theme-toggle" onClick={toggleTheme} title={theme === 'light' ? 'Switch to Dark Mode' : 'Switch to Light Mode'}>
            {theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
          </button>
        </header>

        {/* Scrollable Content Area */}
        <div className="content-area">
          {currentView === 'dashboard' && renderDashboardView()}
          {currentView === 'incidents' && renderIncidentsView()}
          {currentView === 'settings' && renderSettingsView()}
        </div>
      </main>

      {/* ─── Manual Disposition Modal ────────────────────────────────── */}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h3 className="modal__title">Manual Triage Report</h3>

            <div className="modal__field">
              <label className="modal__label">Root Cause <span style={{color: '#ef4444'}}>*</span></label>
              <input className="modal__input" placeholder="e.g., DB_OUTAGE"
                value={modalForm.rootCause} onChange={e => setModalForm(p => ({...p, rootCause: e.target.value}))} />
            </div>
            <div className="modal__field">
              <label className="modal__label">RCA Summary <span style={{color: '#ef4444'}}>*</span></label>
              <textarea className="modal__textarea" placeholder="Brief summary of what happened..."
                value={modalForm.rcaSummary} onChange={e => setModalForm(p => ({...p, rcaSummary: e.target.value}))} />
            </div>
            <div className="modal__field">
              <label className="modal__label">Impact Analysis <span style={{color: '#ef4444'}}>*</span></label>
              <textarea className="modal__textarea" placeholder="Affected systems..."
                value={modalForm.impactAnalysis} onChange={e => setModalForm(p => ({...p, impactAnalysis: e.target.value}))} />
            </div>
            <div className="modal__field">
              <label className="modal__label">Suggested Fix <span style={{color: '#ef4444'}}>*</span></label>
              <textarea className="modal__textarea" placeholder="Steps to mitigate..."
                value={modalForm.suggestedFix} onChange={e => setModalForm(p => ({...p, suggestedFix: e.target.value}))} />
            </div>
            <div className="modal__field">
              <label className="modal__label">Prevention <span style={{color: '#ef4444'}}>*</span></label>
              <textarea className="modal__textarea" placeholder="Future prevention..."
                value={modalForm.prevention} onChange={e => setModalForm(p => ({...p, prevention: e.target.value}))} />
            </div>

            <div className="modal__actions">
              <button className="btn" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={handleManualDisposition}>Submit Report</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
