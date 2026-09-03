import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import App from './App';
import { api } from './api';

jest.mock('./api', () => ({
  api: {
    getIncidents: jest.fn(),
    getStats: jest.fn(),
    getHealth: jest.fn(),
    getSimulationStatus: jest.fn(),
    getChaosScenarios: jest.fn(),
    getChaosServices: jest.fn(),
    getApiKey: jest.fn(),
    getSimConfig: jest.fn(),
    generateResolution: jest.fn(),
    testLLM: jest.fn()
  }
}));

// Mock BootScreen so App unit tests test the main dashboard
jest.mock('./BootScreen', () => {
  const React = require('react');
  return {
    __esModule: true,
    default: ({ onReady }) => {
      React.useEffect(() => {
        if (onReady) onReady();
      }, [onReady]);
      return null;
    }
  };
});

// Mock recharts to avoid rendering issues in JSDOM
jest.mock('recharts', () => {
  const Original = jest.requireActual('recharts');
  return {
    ...Original,
    ResponsiveContainer: ({ children }) => (
      <div style={{ width: 500, height: 300 }}>{children}</div>
    )
  };
});

describe('App', () => {
  beforeEach(() => {
    api.getIncidents.mockResolvedValue({
      content: [
        {
          id: 'INC-123',
          incidentNumber: 'INC-123',
          title: 'Database connection failed',
          severity: 'HIGH',
          status: 'OPEN',
          serviceName: 'user-service',
          description: 'Cannot connect to postgres',
          createdAt: new Date().toISOString()
        }
      ],
      totalElements: 1
    });
    api.getStats.mockResolvedValue({
      totalIncidents: 10,
      openIncidents: 1,
      avgResolutionTime: 120,
      uptime: 99.9,
      totalAnomalies: 20
    });
    api.getHealth.mockResolvedValue({
      status: 'UP',
      components: {
        'user-service': { status: 'UP' }
      }
    });
    api.getSimulationStatus.mockResolvedValue({ active: false });
    api.getChaosScenarios.mockResolvedValue([{ id: 'latency', name: 'Latency' }]);
    api.getChaosServices.mockResolvedValue(['user-service']);
    api.getApiKey.mockResolvedValue({ maskedKey: '****' });
    api.getSimConfig.mockResolvedValue({ logsPerSecond: 5 });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('renders dashboard and fetches data', async () => {
    render(<App />);
    
    // Check if the title is present
    expect(screen.getByText('Sentinel AI')).toBeInTheDocument();
    
    // Check stats are rendered on the dashboard
    await waitFor(() => {
      expect(screen.getByText('10')).toBeInTheDocument(); // total incidents
    });
  });

  test('navigates to incidents view and shows incidents', async () => {
    render(<App />);

    // Click on Incidents navigation item
    const navItems = screen.getAllByText(/Incidents/i);
    const incidentsNav = navItems.find(el => el.classList && el.classList.contains('nav-item'));
    if (incidentsNav) {
      fireEvent.click(incidentsNav);
    }

    // Wait for the incidents to be loaded
    await waitFor(() => {
      expect(screen.getByText('Database connection failed')).toBeInTheDocument();
    });
  });
});
