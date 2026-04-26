import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { settingsApi, type UpdateSettingsRequest } from '@/api/settings.api';
import { useSettingsStore } from '@/store/settings.store';
import i18n from '@/i18n/config';
import { useThemeStore, applyTheme, type Theme } from '@/store/theme.store';

export function useSettings() {
  const setSettings = useSettingsStore((s) => s.setSettings);
  const setTheme = useThemeStore((s) => s.setTheme);

  const query = useQuery({
    queryKey: ['settings'],
    queryFn: settingsApi.get,
    staleTime: 60_000,
  });

  useEffect(() => {
    if (!query.data) return;
    setSettings(query.data);
    if (i18n.resolvedLanguage !== query.data.language) {
      void i18n.changeLanguage(query.data.language);
    }
    const theme = query.data.theme as Theme;
    if (['light', 'dark', 'system'].includes(theme)) {
      setTheme(theme);
      applyTheme(theme);
    }
  }, [query.data, setSettings, setTheme]);

  return query;
}

export function useUpdateSettings() {
  const qc = useQueryClient();
  const setSettings = useSettingsStore((s) => s.setSettings);
  return useMutation({
    mutationFn: (data: UpdateSettingsRequest) => settingsApi.update(data),
    onSuccess: (data) => {
      setSettings(data);
      void qc.setQueryData(['settings'], data);
    },
  });
}

export function useCompleteOnboarding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => settingsApi.completeOnboarding(),
    onSuccess: (data) => {
      void qc.setQueryData(['settings'], data);
    },
  });
}
