import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface Settings {
  currency: string;
  language: string;
  theme: string;
  timezone: string;
}

interface SettingsStore {
  settings: Settings;
  setSettings: (s: Partial<Settings>) => void;
}

const DEFAULT_SETTINGS: Settings = {
  currency: 'TRY',
  language: 'tr',
  theme: 'dark',
  timezone: 'Europe/Istanbul',
};

export const useSettingsStore = create<SettingsStore>()(
  persist(
    (set) => ({
      settings: DEFAULT_SETTINGS,
      setSettings: (partial) =>
        set((state) => ({ settings: { ...state.settings, ...partial } })),
    }),
    { name: 'fintrack-settings' }
  )
);
