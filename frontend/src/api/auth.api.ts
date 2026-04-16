import client from './client';
import type { AuthResponse, TotpSetup, TotpStatus } from '@/types/auth.types';

/** Auth API module. */
export const authApi = {
  register: (data: { username: string; email: string; password: string }) =>
    client.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: { username: string; password: string }) =>
    client.post<AuthResponse>('/auth/login', data).then((r) => r.data),

  refresh: (refreshToken: string) =>
    client.post<AuthResponse>('/auth/refresh', { refreshToken }).then((r) => r.data),

  logout: (refreshToken: string) =>
    client.post('/auth/logout', { refreshToken }),

  verifyTotp: (data: { challengeToken: string; code: string }) =>
    client.post<AuthResponse>('/auth/2fa/verify', data).then((r) => r.data),

  totpStatus: () => client.get<TotpStatus>('/auth/2fa/status').then((r) => r.data),

  totpSetup: () => client.post<TotpSetup>('/auth/2fa/setup').then((r) => r.data),

  totpEnable: (code: string) =>
    client.post('/auth/2fa/enable', { code }).then((r) => r.data),

  totpDisable: (password: string) =>
    client.post('/auth/2fa/disable', { password }).then((r) => r.data),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    client.post('/auth/password', data).then((r) => r.data),

  confirmEmailVerification: (token: string) =>
    client.post('/auth/email-verify/confirm', { token }).then((r) => r.data),

  resendEmailVerification: () =>
    client.post('/auth/email-verify/resend').then((r) => r.data),

  requestPasswordReset: (email: string) =>
    client.post('/auth/password-reset/request', { email }).then((r) => r.data),

  confirmPasswordReset: (data: { token: string; newPassword: string }) =>
    client.post('/auth/password-reset/confirm', data).then((r) => r.data),
};
