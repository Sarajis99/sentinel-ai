import { useState, useEffect, useRef } from 'react';
import { API_BASE } from './api';

const isLocal = typeof window !== 'undefined' && window.location.hostname === 'localhost';

const SERVICES = [
  {
    id: 'api',
    name: 'API Gateway',
    getUrl: () => `${API_BASE}/health`,
  },
  {
    id: 'ingestion',
    name: 'Log Ingestion',
    getUrl: () => isLocal ? 'http://localhost:8081/ping' : (import.meta.env.VITE_INGESTION_URL || 'https://sentinel-ingestion.onrender.com/ping'),
  },
  {
    id: 'detector',
    name: 'Anomaly Detector',
    getUrl: () => isLocal ? 'http://localhost:8082/ping' : (import.meta.env.VITE_DETECTOR_URL || 'https://sentinel-detector-c3ns.onrender.com/ping'),
  },
  {
    id: 'rca',
    name: 'RCA Engine',
    getUrl: () => isLocal ? 'http://localhost:8083/ping' : (import.meta.env.VITE_RCA_URL || 'https://sentinel-rca.onrender.com/ping'),
  },
];

export default function BootScreen({ onReady }) {
  const [statuses, setStatuses] = useState({
    api: 'Starting...',
    ingestion: 'Starting...',
    detector: 'Starting...',
    rca: 'Starting...',
  });

  const statusesRef = useRef(statuses);
  statusesRef.current = statuses;

  useEffect(() => {
    let mounted = true;
    const abortControllers = [];

    // Worker function for each service that patiently pings until UP
    const pingWorker = async (svc) => {
      while (mounted && statusesRef.current[svc.id] !== 'UP') {
        const controller = new AbortController();
        abortControllers.push(controller);

        // 220s timeout gives Render's free tier plenty of time to cold boot (~160-190s)
        const timeoutId = setTimeout(() => controller.abort(), 220000);

        try {
          const res = await fetch(svc.getUrl(), {
            method: 'GET',
            signal: controller.signal,
            cache: 'no-store',
          });
          clearTimeout(timeoutId);

          if (res.ok) {
            if (!mounted) return;
            setStatuses(prev => {
              const updated = { ...prev, [svc.id]: 'UP' };
              statusesRef.current = updated;

              // Check if all 4 are now UP
              if (Object.values(updated).every(s => s === 'UP')) {
                setTimeout(() => {
                  if (mounted) onReady();
                }, 1000);
              }
              return updated;
            });
            return; // Successfully UP, terminate worker
          }
        } catch {
          clearTimeout(timeoutId);
        }

        // If not UP yet, pause 3 seconds before next patient attempt
        if (mounted && statusesRef.current[svc.id] !== 'UP') {
          await new Promise(r => setTimeout(r, 3000));
        }
      }
    };

    // Kick off all 4 service wake-up workers concurrently from the browser
    SERVICES.forEach(svc => {
      pingWorker(svc);
    });

    return () => {
      mounted = false;
      abortControllers.forEach(c => {
        try { c.abort(); } catch {}
      });
    };
  }, [onReady]);

  const serviceList = SERVICES.map(svc => ({
    name: svc.name,
    status: statuses[svc.id] || 'Starting...',
  }));

  const upCount = serviceList.filter(s => s.status === 'UP').length;
  const progressPercent = (upCount / SERVICES.length) * 100;

  return (
    <div className="boot-screen">
      <div className="boot-logo">🛡️</div>
      <h1 className="boot-title">Sentinel AI — Booting Up</h1>
      <p className="boot-subtitle">
        Microservices are waking up from sleep mode. Because this project runs on free-tier servers, a cold boot takes <b>2 to 3 minutes</b>.
      </p>

      <div className="boot-services">
        {serviceList.map(s => (
          <div key={s.name} className={`boot-service ${s.status === 'UP' ? 'ready' : ''}`}>
            <div className={`boot-service-icon ${s.status === 'UP' ? '' : 'spinning'}`}>
              {s.status === 'UP' ? '✅' : '⏳'}
            </div>
            <div className="boot-service-name">{s.name}</div>
            <div className={`boot-service-status ${s.status === 'UP' ? 'up' : 'starting'}`}>
              {s.status === 'UP' ? 'UP' : 'Starting...'}
            </div>
          </div>
        ))}
      </div>

      <div className="boot-progress">
        <div
          className="boot-progress-bar"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
    </div>
  );
}
