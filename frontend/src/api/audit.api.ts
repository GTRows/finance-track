import client from './client';

export interface AuditLogEntry {
  id: number;
  userId: string | null;
  username: string | null;
  action: string;
  status: 'SUCCESS' | 'FAILURE';
  ipAddress: string | null;
  userAgent: string | null;
  detail: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  items: AuditLogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const auditApi = {
  list: (params: { page?: number; size?: number; action?: string } = {}) =>
    client
      .get<AuditLogPage>('/admin/audit', { params })
      .then((r) => r.data),
};
