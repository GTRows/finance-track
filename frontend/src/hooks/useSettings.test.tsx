import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { settingsApi } from '@/api/settings.api';
import { useSettingsStore } from '@/store/settings.store';
import {
  useCompleteOnboarding,
  useSettings,
  useUpdateSettings,
} from './useSettings';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/settings.api', () => ({
  settingsApi: {
    get: vi.fn(),
    update: vi.fn(),
    completeOnboarding: vi.fn(),
  },
}));

vi.mock('@/i18n/config', () => ({
  default: { resolvedLanguage: 'en', changeLanguage: vi.fn() },
}));

describe('useSettings hooks', () => {
  beforeEach(() => {
    vi.mocked(settingsApi.get).mockReset();
    vi.mocked(settingsApi.update).mockReset();
    vi.mocked(settingsApi.completeOnboarding).mockReset();
    useSettingsStore.setState({
      settings: { currency: 'TRY', language: 'tr', theme: 'dark', timezone: 'Europe/Istanbul' },
    });
  });

  it('useSettings populates the settings store on success', async () => {
    const data = {
      currency: 'TRY',
      language: 'en',
      theme: 'light',
      timezone: 'Europe/Istanbul',
      onboardingCompleted: true,
    };
    vi.mocked(settingsApi.get).mockResolvedValueOnce(data as never);

    const { Wrapper } = createWrapper();
    const { result } = renderHook(() => useSettings(), { wrapper: Wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await waitFor(() => expect(useSettingsStore.getState().settings).toEqual(data));
  });

  it('useUpdateSettings updates the store and cache', async () => {
    const updated = {
      currency: 'USD',
      language: 'en',
      theme: 'dark',
      timezone: 'UTC',
      onboardingCompleted: true,
    };
    vi.mocked(settingsApi.update).mockResolvedValueOnce(updated as never);

    const { Wrapper, client } = createWrapper();
    const { result } = renderHook(() => useUpdateSettings(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync(updated as never);
    });

    expect(useSettingsStore.getState().settings).toEqual(updated);
    expect(client.getQueryData(['settings'])).toEqual(updated);
  });

  it('useCompleteOnboarding seeds the settings cache', async () => {
    const data = { onboardingCompleted: true } as never;
    vi.mocked(settingsApi.completeOnboarding).mockResolvedValueOnce(data);

    const { Wrapper, client } = createWrapper();
    const { result } = renderHook(() => useCompleteOnboarding(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync();
    });

    expect(client.getQueryData(['settings'])).toEqual(data);
  });
});
