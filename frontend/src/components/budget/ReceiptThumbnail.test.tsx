import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { ReceiptThumbnail } from './ReceiptThumbnail';
import { receiptApi } from '@/api/receipt.api';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('@/api/receipt.api', () => ({
  receiptApi: {
    signedUrl: vi.fn(),
  },
}));

describe('ReceiptThumbnail', () => {
  beforeEach(() => {
    vi.mocked(receiptApi.signedUrl).mockReset();
  });

  it('renders an <img> with the signed URL once the query resolves', async () => {
    vi.mocked(receiptApi.signedUrl).mockResolvedValue({
      url: 'http://localhost/api/v1/budget/transactions/abc/receipt?token=xyz',
      expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
    });
    const { Wrapper } = createWrapper();

    render(<ReceiptThumbnail transactionId="abc" alt="receipt-alt" />, { wrapper: Wrapper });

    const img = await waitFor(() => screen.getByAltText<HTMLImageElement>('receipt-alt'));
    expect(img.src).toBe('http://localhost/api/v1/budget/transactions/abc/receipt?token=xyz');
    expect(receiptApi.signedUrl).toHaveBeenCalledWith('abc');
  });
});
