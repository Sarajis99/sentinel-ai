const API_BASE = 'http://localhost:8084/api/v1';

async function fetchJSON(url, options = {}) {
  const res = await fetch(`${API_BASE}${url}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(error.error || error.message || `HTTP ${res.status}`);
  }
  return res.json();
}

export const api = {
  // Incidents
  getIncidents: (page = 0, size = 20, filters = {}) => {
    const params = new URLSearchParams({ page, size });
    if (filters.severity) params.append('severity', filters.severity);
    if (filters.status) params.append('status', filters.status);
    if (filters.service) params.append('service', filters.service);
    return fetchJSON(`/incidents?${params}`);
  },
  getIncident: (id) => fetchJSON(`/incidents/${id}`),
  getUnknownIncidents: () => fetchJSON('/incidents/unknown'),
  getStats: () => fetchJSON('/incidents/stats'),

  // Actions
  resolveIncident: (id) => fetchJSON(`/incidents/${id}/resolve`, { method: 'POST' }),
  dismissIncident: (id) => fetchJSON(`/incidents/${id}/dismiss`, { method: 'POST' }),
  manualDisposition: (id, body) => fetchJSON(`/incidents/${id}/manual-disposition`, {
    method: 'PUT',
    body: JSON.stringify(body),
  }),

  // New lifecycle actions
  acceptIncident: (id) => fetchJSON(`/incidents/${id}/accept`, { method: 'POST' }),
  closeIncident: (id) => fetchJSON(`/incidents/${id}/close`, { method: 'POST' }),
  retryAnalysis: (id) => fetchJSON(`/incidents/${id}/retry-analysis`, { method: 'POST' }),

  // Comments
  getComments: (id) => fetchJSON(`/incidents/${id}/comments`),
  addComment: (id, body) => fetchJSON(`/incidents/${id}/comments`, {
    method: 'POST',
    body: JSON.stringify(body),
  }),

  // System
  triggerSimulation: () => fetchJSON('/system/simulate', { method: 'POST' }),
  getSimulationStatus: () => fetchJSON('/system/simulation-status'),
  getHealth: () => fetchJSON('/health'),
};
