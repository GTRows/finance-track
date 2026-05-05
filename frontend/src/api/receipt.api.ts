import client from './client';

export interface StoredReceipt {
  relativePath: string;
  mimeType: string;
  bytes: number;
}

export interface SignedReceiptUrl {
  url: string;
  expiresAt: string;
}

export const receiptApi = {
  upload: async (transactionId: string, file: File): Promise<StoredReceipt> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await client.post<StoredReceipt>(
      `/budget/transactions/${transactionId}/receipt`,
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return data;
  },

  downloadUrl: (transactionId: string): string => {
    const base = client.defaults.baseURL ?? '';
    return `${base}/budget/transactions/${transactionId}/receipt`;
  },

  download: async (transactionId: string): Promise<Blob> => {
    const { data } = await client.get<Blob>(
      `/budget/transactions/${transactionId}/receipt`,
      { responseType: 'blob' }
    );
    return data;
  },

  signedUrl: async (transactionId: string): Promise<SignedReceiptUrl> => {
    const { data } = await client.get<SignedReceiptUrl>(
      `/budget/transactions/${transactionId}/receipt/url`
    );
    return data;
  },

  remove: async (transactionId: string): Promise<void> => {
    await client.delete(`/budget/transactions/${transactionId}/receipt`);
  },
};
