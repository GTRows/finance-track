import client from './client';
import type { AuthResponse } from '@/types/auth.types';

/** Auth API module. */
export const authApi = {
  /** Registers a new user and returns token pair. */
  register: (data: { username: string; email: string; password: string }) =>
    client.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  /** Authenticates a user and returns token pair. */
  login: (data: { username: string; password: string }) =>
    client.post<AuthResponse>('/auth/login', data).then((r) => r.data),

  /** Refreshes the access token using the refresh token. */
  refresh: (refreshToken: string) =>
    client.post<AuthResponse>('/auth/refresh', { refreshToken }).then((r) => r.data),

  /** Revokes the refresh token (logout). */
  logout: (refreshToken: string) =>
    client.post('/auth/logout', { refreshToken }),
};
