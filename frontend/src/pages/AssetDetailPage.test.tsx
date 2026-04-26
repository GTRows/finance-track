import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { AssetDetailPage } from './AssetDetailPage';
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

vi.mock('@/api/asset.api', () => ({
  assetApi: { get: vi.fn(), history: vi.fn(), list: vi.fn() },
}));

vi.mock('@/api/price.api', () => ({
  priceApi: { refresh: vi.fn(), refreshAsset: vi.fn() },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter initialEntries={['/assets/asset-1']}>
      <Routes>
        <Route path="/assets/:id" element={<AssetDetailPage />} />
      </Routes>
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('AssetDetailPage', () => {
  beforeEach(() => {
    vi.mocked(assetApi.get).mockReset();
    vi.mocked(assetApi.history).mockReset();
    vi.mocked(assetApi.history).mockResolvedValue([] as never);
  });

  it('renders the asset header when the asset loads', async () => {
    vi.mocked(assetApi.get).mockResolvedValue({
      id: 'asset-1',
      symbol: 'BTC',
      name: 'Bitcoin',
      assetType: 'CRYPTO',
      currency: 'USD',
      price: 60000,
      priceUsd: 60000,
      priceUpdatedAt: '2026-04-01T00:00:00Z',
    } as never);

    renderPage();

    expect(await screen.findByText(/BTC.*Bitcoin/)).toBeDefined();
  });

  it('falls back to error message when the asset cannot be loaded', async () => {
    vi.mocked(assetApi.get).mockResolvedValue(null as never);

    renderPage();

    expect(await screen.findByText('common.somethingWentWrong')).toBeDefined();
  });
});
