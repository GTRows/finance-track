import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { SettingsPage } from './SettingsPage';
import { settingsApi } from '@/api/settings.api';
import { useAuthStore } from '@/store/auth.store';
import { useSettingsStore } from '@/store/settings.store';
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

vi.mock('@/api/settings.api', () => ({
  settingsApi: {
    get: vi.fn().mockResolvedValue({
      currency: 'TRY',
      language: 'en',
      theme: 'dark',
      timezone: 'Europe/Istanbul',
    }),
    update: vi.fn(),
    completeOnboarding: vi.fn(),
  },
}));

vi.mock('@/api/tags.api', () => ({
  tagsApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

vi.mock('@/api/category-rules.api', () => ({
  categoryRulesApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/api/backup.api', () => ({
  backupApi: { export: vi.fn(), restore: vi.fn() },
}));

vi.mock('@/api/auth.api', () => ({
  authApi: {
    listSessions: vi.fn().mockResolvedValue([]),
    revokeSession: vi.fn(),
    revokeOtherSessions: vi.fn(),
    changePassword: vi.fn(),
    enableTotp: vi.fn(),
    disableTotp: vi.fn(),
    regenerateRecoveryCodes: vi.fn(),
    listAuditLog: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('@/api/push.api', () => ({
  pushApi: { getVapidPublicKey: vi.fn(), subscribe: vi.fn(), unsubscribe: vi.fn(), test: vi.fn() },
}));

function renderPage() {
  const { Wrapper } = createWrapper();
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
    { wrapper: Wrapper },
  );
}

describe('SettingsPage', () => {
  beforeEach(() => {
    Object.values(settingsApi).forEach((fn) => vi.mocked(fn).mockReset());
    vi.mocked(settingsApi.get).mockResolvedValue({
      currency: 'TRY',
      language: 'en',
      theme: 'dark',
      timezone: 'Europe/Istanbul',
    } as never);
    useAuthStore.setState({
      user: {
        id: 'u1',
        username: 'ali',
        email: 'a@b',
        role: 'USER',
        emailVerified: true,
        createdAt: '2026-01-01',
      },
      accessToken: 't',
      refreshToken: 'r',
    });
    useSettingsStore.setState({
      settings: { currency: 'TRY', language: 'en', theme: 'dark', timezone: 'Europe/Istanbul' },
    });
  });

  it('renders the page header on mount', () => {
    renderPage();
    expect(screen.getAllByText('settings.title').length).toBeGreaterThan(0);
  });
});
