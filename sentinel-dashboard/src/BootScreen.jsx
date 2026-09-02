import { useState, useEffect } from 'react';
import { api, pingAPI } from './api';

export default function BootScreen({ onReady }) {
  const [apiStatus, setApiStatus] = useState('Starting...');
  const [services, setServices] = useState({
    'Log Ingestion': 'Starting...',
    'Anomaly Detector': 'Starting...',
    'RCA Engine': 'Starting...',
  });

  useEffect(() => {
    let interval;
    let mounted = true;

    const checkStatus = async () => {
      const apiOk = await pingAPI();
      if (!mounted) return;

      if (!apiOk) {
        setApiStatus('Starting...');
        return;
      } else {
        setApiStatus('UP');
      }

      try {
        const statuses = await api.getServicesStatus();
        if (!mounted) return;

        let parsed = statuses.services || statuses;

        const mapKey = (key) => {
          const k = key.toLowerCase();
          if (k.includes('log')) return 'Log Ingestion';
          if (k.includes('anomaly')) return 'Anomaly Detector';
          if (k.includes('rca')) return 'RCA Engine';
          return key;
        };

        const nextServs = {
          'Log Ingestion': 'Starting...',
          'Anomaly Detector': 'Starting...',
          'RCA Engine': 'Starting...',
        };

        for (const [k, v] of Object.entries(parsed)) {
          const name = mapKey(k);
          if (nextServs[name] !== undefined) {
            nextServs[name] = typeof v === 'string' ? v : (v.status || 'Starting...');
          }
        }

        setServices(nextServs);

        if (apiOk && Object.values(nextServs).every(s => s === 'UP')) {
          clearInterval(interval);
          setTimeout(() => {
            if (mounted) onReady();
          }, 1000);
        }
      } catch (e) {
        // API is up but services-status failed
      }
    };

    checkStatus();
    interval = setInterval(checkStatus, 3000);

    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, [onReady]);

  const allServices = [
    { name: 'API Gateway', status: apiStatus },
    { name: 'Log Ingestion', status: services['Log Ingestion'] },
    { name: 'Anomaly Detector', status: services['Anomaly Detector'] },
    { name: 'RCA Engine', status: services['RCA Engine'] },
  ];

  return (
    <div className="boot-screen">
      <div className="boot-logo">🛡️</div>
      <h1 className="boot-title">Sentinel AI — Booting Up</h1>
      <p className="boot-subtitle">Microservices are waking up from sleep mode. Because this project runs on free-tier servers, a cold boot takes <b>2 to 3 minutes</b>.</p>

      <div className="boot-services">
        {allServices.map(s => (
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
          style={{ width: `${(allServices.filter(s => s.status === 'UP').length / 4) * 100}%` }}
        ></div>
      </div>
    </div>
  );
}
