import client from './client';

export interface SessionInfo {
  id: string;
  userAgent: string | null;
  ipAddress: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string;
  current: boolean;
}

export const sessionsApi = {
  list: (refreshToken: string | null) =>
    client
      .post<SessionInfo[]>('/auth/sessions/list', { refreshToken })
      .then((r) => r.data),

  revoke: (id: string) =>
    client.delete(`/auth/sessions/${id}`).then((r) => r.data),

  revokeOthers: (refreshToken: string) =>
    client
      .post<{ revoked: number }>('/auth/sessions/revoke-others', { refreshToken })
      .then((r) => r.data),
};
