import { beforeEach, describe, expect, it } from 'vitest';
import { useSettingsStore } from './settings.store';

describe('useSettingsStore', () => {
  beforeEach(() => {
    useSettingsStore.setState({
      settings: { currency: 'TRY', language: 'tr', theme: 'dark', timezone: 'Europe/Istanbul' },
    });
    window.localStorage.clear();
  });

  it('exposes default TRY / tr / dark / Europe/Istanbul values', () => {
    const { settings } = useSettingsStore.getState();
    expect(settings).toEqual({
      currency: 'TRY',
      language: 'tr',
      theme: 'dark',
      timezone: 'Europe/Istanbul',
    });
  });

  it('setSettings merges a partial update without touching untouched keys', () => {
    useSettingsStore.getState().setSettings({ currency: 'USD' });
    expect(useSettingsStore.getState().settings).toEqual({
      currency: 'USD',
      language: 'tr',
      theme: 'dark',
      timezone: 'Europe/Istanbul',
    });
  });

  it('supports updating multiple fields at once', () => {
    useSettingsStore.getState().setSettings({ language: 'en', theme: 'light' });
    const s = useSettingsStore.getState().settings;
    expect(s.language).toBe('en');
    expect(s.theme).toBe('light');
    expect(s.currency).toBe('TRY');
  });

  it('persists settings to localStorage under the fintrack-settings key', () => {
    useSettingsStore.getState().setSettings({ currency: 'EUR', language: 'en' });

    const raw = window.localStorage.getItem('fintrack-settings');
    expect(raw).toBeTruthy();
    const parsed = JSON.parse(raw!);
    expect(parsed.state.settings.currency).toBe('EUR');
    expect(parsed.state.settings.language).toBe('en');
  });
});
