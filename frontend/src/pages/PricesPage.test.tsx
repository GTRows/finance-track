import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { PricesPage } from './PricesPage';
import { assetApi } from '@/api/asset.api';
import { watchlistApi } from '@/api/watchlist.api';
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
  assetApi: {
    list: vi.fn().mockResolvedValue([]),
    get: vi.fn(),
    history: vi.fn(),
    searchTefas: vi.fn().mockResolvedValue([]),
    importTefas: vi.fn(),
  },
}));

vi.mock('@/api/watchlist.api', () => ({
  watchlistApi: { list: vi.fn().mockResolvedValue([]), add: vi.fn(), remove: vi.fn() },
}));

vi.mock('@/api/price.api', () => ({
  priceApi: { refresh: vi.fn(), refreshAsset: vi.fn() },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <PricesPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('PricesPage', () => {
  beforeEach(() => {
    vi.mocked(assetApi.list).mockReset();
    vi.mocked(watchlistApi.list).mockReset();
    vi.mocked(assetApi.list).mockResolvedValue([] as never);
    vi.mocked(watchlistApi.list).mockResolvedValue([] as never);
  });

  it('renders the page header on empty data', async () => {
    renderPage();
    const titles = await screen.findAllByText('prices.title');
    expect(titles.length).toBeGreaterThan(0);
  });
});
