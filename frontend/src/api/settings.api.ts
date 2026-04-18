import client from './client';

export interface SettingsResponse {
  currency: string;
  language: string;
  theme: string;
  timezone: string;
  onboardingCompleted: boolean;
}

export interface UpdateSettingsRequest {
  currency?: string;
  language?: string;
  theme?: string;
  timezone?: string;
}

export const settingsApi = {
  get: () => client.get<SettingsResponse>('/settings').then((r) => r.data),
  update: (data: UpdateSettingsRequest) =>
    client.put<SettingsResponse>('/settings', data).then((r) => r.data),
  completeOnboarding: () =>
    client.post<SettingsResponse>('/settings/onboarding-complete').then((r) => r.data),
};
