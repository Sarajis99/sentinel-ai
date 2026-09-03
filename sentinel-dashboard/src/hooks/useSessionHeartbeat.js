import { useState, useEffect, useRef, useCallback } from 'react';

const isLocal = typeof window !== 'undefined' && window.location.hostname === 'localhost';

const HEARTBEAT_SERVICES = [
  {
    id: 'ingestion',
    name: 'Log Ingestion',
    url: isLocal ? 'http://localhost:8081/ping' : (import.meta.env.VITE_INGESTION_URL || 'https://sentinel-ingestion.onrender.com/ping'),
  },
  {
    id: 'detector',
    name: 'Anomaly Detector',
    url: isLocal ? 'http://localhost:8082/ping' : (import.meta.env.VITE_DETECTOR_URL || 'https://sentinel-detector-c3ns.onrender.com/ping'),
  },
  {
    id: 'rca',
    name: 'RCA Engine',
    url: isLocal ? 'http://localhost:8083/ping' : (import.meta.env.VITE_RCA_URL || 'https://sentinel-rca.onrender.com/ping'),
  },
];

// Configuration constants
const HEARTBEAT_INTERVAL_MS = 8 * 60 * 1000;   // Ping every 8 minutes (Render sleeps at 15m)
const INACTIVITY_TIMEOUT_MS = 20 * 60 * 1000;   // 20 minutes without user interaction triggers idle
const HIDDEN_TIMEOUT_MS     = 15 * 60 * 1000;   // 15 minutes in background tab triggers idle

/**
 * useSessionHeartbeat
 *
 * Keeps downstream microservices awake while a user is actively viewing/using the dashboard.
 * Halts all heartbeats and enters Eco-Mode if the user goes idle or backgrounds the tab,
 * preserving Render's 750 free-tier compute hours quota.
 */
export function useSessionHeartbeat({ enabled = true, onResume } = {}) {
  const [isIdle, setIsIdle] = useState(false);
  const lastActiveRef = useRef(Date.now());
  const hiddenSinceRef = useRef(null);
  const isIdleRef = useRef(false);
  isIdleRef.current = isIdle;

  // Send a lightweight ping to all 3 background microservices
  const sendHeartbeat = useCallback(async () => {
    if (isIdleRef.current || document.visibilityState === 'hidden') {
      return;
    }

    // Ping all 3 services concurrently with a short 8s timeout
    HEARTBEAT_SERVICES.forEach(svc => {
      fetch(svc.url, {
        method: 'GET',
        signal: AbortSignal.timeout(8000),
        cache: 'no-store',
      }).catch(() => {
        // Silent catch — if a service already slept, it will be woken up on next check or resume
      });
    });
  }, []);

  // Activity handler — throttled to at most once every 10 seconds
  const handleActivity = useCallback(() => {
    if (isIdleRef.current) return;
    const now = Date.now();
    if (now - lastActiveRef.current > 10000) {
      lastActiveRef.current = now;
    }
  }, []);

  // Resume handler called by user when clicking "Resume Session"
  const resumeSession = useCallback(() => {
    setIsIdle(false);
    lastActiveRef.current = Date.now();
    hiddenSinceRef.current = null;
    if (onResume) {
      onResume();
    }
  }, [onResume]);

  useEffect(() => {
    if (!enabled) return;

    // 1. Attach user interaction event listeners
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach(evt => window.addEventListener(evt, handleActivity, { passive: true }));

    // 2. Page Visibility Listener (tab switched or minimized)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        hiddenSinceRef.current = Date.now();
      } else {
        // User returned to tab
        if (hiddenSinceRef.current) {
          const elapsed = Date.now() - hiddenSinceRef.current;
          if (elapsed >= HIDDEN_TIMEOUT_MS) {
            setIsIdle(true);
          }
          hiddenSinceRef.current = null;
        }
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

    // 3. Periodic Activity & Idle Check (every 30 seconds)
    const checkInterval = setInterval(() => {
      if (isIdleRef.current) return;

      const idleDuration = Date.now() - lastActiveRef.current;
      if (idleDuration >= INACTIVITY_TIMEOUT_MS) {
        setIsIdle(true);
      }
    }, 30000);

    // 4. Periodic Heartbeat Ping (every 8 minutes)
    const heartbeatInterval = setInterval(() => {
      sendHeartbeat();
    }, HEARTBEAT_INTERVAL_MS);

    // Initial heartbeat after 30s of healthy session to establish the baseline
    const initialTimer = setTimeout(() => {
      sendHeartbeat();
    }, 30000);

    return () => {
      events.forEach(evt => window.removeEventListener(evt, handleActivity));
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      clearInterval(checkInterval);
      clearInterval(heartbeatInterval);
      clearTimeout(initialTimer);
    };
  }, [enabled, handleActivity, sendHeartbeat]);

  return {
    isIdle,
    resumeSession,
  };
}
