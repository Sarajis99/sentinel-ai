import { api } from './api';

global.fetch = jest.fn();

describe('api', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('getIncidents calls correct URL', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [] })
    });

    const res = await api.getIncidents(0, 10);
    expect(global.fetch).toHaveBeenCalledWith('http://localhost:8084/api/v1/incidents?page=0&size=10', expect.any(Object));
    expect(res).toEqual({ content: [] });
  });

  test('getStats calls correct URL', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ total: 10 })
    });

    const res = await api.getStats();
    expect(global.fetch).toHaveBeenCalledWith('http://localhost:8084/api/v1/incidents/stats', expect.any(Object));
    expect(res).toEqual({ total: 10 });
  });

  test('getHealth calls correct URL', async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ status: 'UP' })
    });

    const res = await api.getHealth();
    expect(global.fetch).toHaveBeenCalledWith('http://localhost:8084/api/v1/health', expect.any(Object));
    expect(res).toEqual({ status: 'UP' });
  });
});
