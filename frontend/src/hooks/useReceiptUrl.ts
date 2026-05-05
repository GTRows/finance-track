import { useQuery } from '@tanstack/react-query';
import { receiptApi } from '@/api/receipt.api';

const FOUR_MINUTES_MS = 4 * 60 * 1000;

export function useReceiptUrl(transactionId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['receipt-url', transactionId],
    queryFn: () => receiptApi.signedUrl(transactionId),
    enabled: enabled && !!transactionId,
    staleTime: FOUR_MINUTES_MS,
    refetchInterval: FOUR_MINUTES_MS,
  });
}
