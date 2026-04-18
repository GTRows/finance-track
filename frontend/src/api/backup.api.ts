import client from './client';

export async function downloadBackup(): Promise<{ blob: Blob; filename: string }> {
  const response = await client.get('/backup/export', { responseType: 'blob' });
  const disposition = response.headers['content-disposition'] as string | undefined;
  const match = disposition?.match(/filename=([^;]+)/i);
  const filename = match ? match[1].trim().replace(/"/g, '') : 'fintrack-backup.json';
  return { blob: response.data as Blob, filename };
}

export async function uploadBackup(payload: unknown): Promise<{
  status: string;
  transactions: number;
  portfolios: number;
  bills: number;
}> {
  const { data } = await client.post('/backup/import', payload);
  return data;
}
