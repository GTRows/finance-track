import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { AlertsPage } from './AlertsPage';
import { alertsApi } from '@/api/alerts.api';
import { assetApi } from '@/api/asset.api';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof I18N>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
    }),
  };
});

vi.mock('@/api/alerts.api', () => ({
  alertsApi: { list: vi.fn(), create: vi.fn(), delete: vi.fn() },
  notificationsApi: { list: vi.fn(), unreadCount: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}));

vi.mock('@/api/asset.api', () => ({
  assetApi: { list: vi.fn().mockResolvedValue([]), get: vi.fn(), history: vi.fn() },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <AlertsPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('AlertsPage', () => {
  beforeEach(() => {
    vi.mocked(alertsApi.list).mockReset();
    vi.mocked(assetApi.list).mockReset();
  });

  it('renders the page header on empty data', async () => {
    vi.mocked(alertsApi.list).mockResolvedValue([] as never);
    vi.mocked(assetApi.list).mockResolvedValue([] as never);

    renderPage();

    await waitFor(() => expect(alertsApi.list).toHaveBeenCalled());
    expect(screen.getAllByText('alerts.title').length).toBeGreaterThan(0);
  });

  it('renders alert rows when data is present', async () => {
    vi.mocked(alertsApi.list).mockResolvedValue([
      {
        id: 'a1',
        assetId: 'x',
        assetSymbol: 'BTC',
        assetName: 'Bitcoin',
        assetType: 'CRYPTO',
        currentPriceTry: 100,
        direction: 'ABOVE',
        thresholdTry: 120,
        status: 'ACTIVE',
        triggeredAt: null,
        createdAt: '2026-04-01T00:00:00Z',
      },
    ] as never);
    vi.mocked(assetApi.list).mockResolvedValue([] as never);

    renderPage();
    await waitFor(() => expect(screen.getAllByText('BTC').length).toBeGreaterThan(0));
  });
});
