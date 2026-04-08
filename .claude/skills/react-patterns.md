---
name: react-patterns
description: React + TypeScript patterns used in this project. Read before writing any frontend code.
---

# React Patterns — FinTrack Frontend

## API Client Setup

```typescript
// src/api/client.ts — ALL API calls go through here
import axios from 'axios';
import { useAuthStore } from '@/store/auth.store';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// Attach token to every request
client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Auto-refresh on 401
let isRefreshing = false;
let failedQueue: Array<{ resolve: Function; reject: Function }> = [];

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return client(originalRequest);
        });
      }
      isRefreshing = true;
      try {
        const refreshToken = useAuthStore.getState().refreshToken;
        const { data } = await axios.post(`${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
          { refreshToken });
        useAuthStore.getState().setAuth(data.user, data.accessToken, data.refreshToken);
        failedQueue.forEach(p => p.resolve(data.accessToken));
        failedQueue = [];
        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return client(originalRequest);
      } catch {
        failedQueue.forEach(p => p.reject(error));
        failedQueue = [];
        useAuthStore.getState().clearAuth();
        window.location.href = '/login';
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);

export default client;
```

## API Module Pattern

```typescript
// src/api/portfolio.api.ts
import client from './client';
import type { Portfolio, CreatePortfolioRequest, InvestmentTransaction } from '@/types/portfolio.types';

export const portfolioApi = {
  getAll: () =>
    client.get<Portfolio[]>('/portfolios').then(r => r.data),

  getById: (id: string) =>
    client.get<Portfolio>(`/portfolios/${id}`).then(r => r.data),

  create: (data: CreatePortfolioRequest) =>
    client.post<Portfolio>('/portfolios', data).then(r => r.data),

  addTransaction: (portfolioId: string, data: AddTransactionRequest) =>
    client.post<PortfolioHolding>(`/portfolios/${portfolioId}/transactions`, data).then(r => r.data),

  getHistory: (id: string, params: { from: string; to: string }) =>
    client.get<SnapshotData[]>(`/portfolios/${id}/history`, { params }).then(r => r.data),
};
```

## React Query Hook Pattern

```typescript
// src/hooks/usePortfolio.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { portfolioApi } from '@/api/portfolio.api';
import { toast } from '@/components/ui/use-toast';

// Query keys — centralized to avoid string typos
export const portfolioKeys = {
  all: ['portfolios'] as const,
  detail: (id: string) => ['portfolios', id] as const,
  history: (id: string) => ['portfolios', id, 'history'] as const,
};

export function usePortfolios() {
  return useQuery({
    queryKey: portfolioKeys.all,
    queryFn: portfolioApi.getAll,
  });
}

export function usePortfolio(id: string) {
  return useQuery({
    queryKey: portfolioKeys.detail(id),
    queryFn: () => portfolioApi.getById(id),
    enabled: !!id,
  });
}

export function useAddTransaction(portfolioId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AddTransactionRequest) =>
      portfolioApi.addTransaction(portfolioId, data),
    onSuccess: () => {
      // Invalidate relevant queries to refetch
      queryClient.invalidateQueries({ queryKey: portfolioKeys.detail(portfolioId) });
      toast({ title: 'İşlem kaydedildi', variant: 'success' });
    },
    onError: () => {
      toast({ title: 'Hata oluştu', variant: 'destructive' });
    },
  });
}
```

## Zustand Store Pattern

```typescript
// src/store/auth.store.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '@/types/auth.types';

interface AuthStore {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  setAuth: (user: User, accessToken: string, refreshToken: string) => void;
  clearAuth: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      setAuth: (user, accessToken, refreshToken) =>
        set({ user, accessToken, refreshToken }),
      clearAuth: () =>
        set({ user: null, accessToken: null, refreshToken: null }),
      isAuthenticated: () => !!get().accessToken,
    }),
    {
      name: 'fintrack-auth',
      // Only persist refreshToken — accessToken is short-lived
      partialize: (state) => ({
        user: state.user,
        refreshToken: state.refreshToken,
      }),
    }
  )
);
```

## WebSocket Hook

```typescript
// src/hooks/useLivePrices.ts
import { useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import { usePricesStore } from '@/store/prices.store';
import { useAuthStore } from '@/store/auth.store';

export function useLivePrices() {
  const setPrices = usePricesStore(s => s.setPrices);
  const token = useAuthStore(s => s.accessToken);

  useEffect(() => {
    if (!token) return;

    const client = new Client({
      brokerURL: import.meta.env.VITE_WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        client.subscribe('/topic/prices', (message) => {
          setPrices(JSON.parse(message.body));
        });
      },
      reconnectDelay: 5000,
    });

    client.activate();
    return () => { client.deactivate(); };
  }, [token, setPrices]);
}
```

## Page Pattern

```typescript
// src/pages/PortfolioPage.tsx
import { useParams } from 'react-router-dom';
import { usePortfolio } from '@/hooks/usePortfolio';
import { HoldingsTable } from '@/components/portfolio/HoldingsTable';
import { AllocationChart } from '@/components/portfolio/AllocationChart';
import { AddTransactionDialog } from '@/components/portfolio/AddTransactionDialog';
import { Skeleton } from '@/components/ui/skeleton';

export function PortfolioPage() {
  const { id } = useParams<{ id: string }>();
  const { data: portfolio, isLoading, error } = usePortfolio(id!);

  if (isLoading) return <PortfolioSkeleton />;
  if (error) return <ErrorState message="Portföy yüklenemedi" />;
  if (!portfolio) return <EmptyState message="Portföy bulunamadı" />;

  return (
    <div className="space-y-6">
      <PortfolioHeader portfolio={portfolio} />
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <HoldingsTable holdings={portfolio.holdings} />
        <AllocationChart holdings={portfolio.holdings} />
      </div>
      <TransactionLog portfolioId={id!} />
      <AddTransactionDialog portfolioId={id!} />
    </div>
  );
}
```

## Component Pattern

```typescript
// src/components/portfolio/HoldingsTable.tsx
import { formatTRY, formatPercent } from '@/utils/formatters';
import { usePricesStore } from '@/store/prices.store';
import type { PortfolioHolding } from '@/types/portfolio.types';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

interface HoldingsTableProps {
  holdings: PortfolioHolding[];
}

export function HoldingsTable({ holdings }: HoldingsTableProps) {
  const prices = usePricesStore(s => s.prices);

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Varlık</TableHead>
          <TableHead className="text-right">Güncel Değer</TableHead>
          <TableHead className="text-right">K/Z</TableHead>
          <TableHead className="text-right">Hedef / Mevcut</TableHead>
          <TableHead className="text-right">Sapma</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {holdings.map((holding) => (
          <HoldingRow key={holding.assetSymbol} holding={holding} livePrice={prices[holding.assetSymbol]} />
        ))}
      </TableBody>
    </Table>
  );
}

function HoldingRow({ holding, livePrice }: { holding: PortfolioHolding; livePrice?: PriceData }) {
  const currentValue = livePrice
    ? holding.quantity * livePrice.priceTry
    : holding.currentValueTry;

  const deviation = holding.currentWeight - holding.targetWeight;
  const deviationColor = Math.abs(deviation) < 0.01
    ? 'text-green-500'
    : Math.abs(deviation) < 0.03
    ? 'text-amber-500'
    : 'text-red-500';

  return (
    <TableRow>
      <TableCell className="font-medium">{holding.assetSymbol}</TableCell>
      <TableCell className="text-right font-mono">{formatTRY(currentValue)}</TableCell>
      <TableCell className={cn('text-right font-mono', holding.pnlTry >= 0 ? 'text-green-500' : 'text-red-500')}>
        {formatPercent(holding.pnlPercent)}
      </TableCell>
      <TableCell className="text-right text-muted-foreground">
        {formatPercent(holding.targetWeight)} / {formatPercent(holding.currentWeight)}
      </TableCell>
      <TableCell className={cn('text-right', deviationColor)}>
        {deviation > 0 ? '+' : ''}{formatPercent(deviation)}
      </TableCell>
    </TableRow>
  );
}
```

## TypeScript Types Pattern

```typescript
// src/types/portfolio.types.ts
// Mirror backend DTOs exactly

export interface Portfolio {
  id: string;
  name: string;
  type: 'BIREYSEL' | 'BES';
  totalValueTry: number;
  totalCostTry: number;
  pnlTry: number;
  pnlPercent: number;
  holdings: PortfolioHolding[];
}

export interface PortfolioHolding {
  assetSymbol: string;
  assetName: string;
  assetType: AssetType;
  quantity: number;
  currentPriceTry: number;
  currentValueTry: number;
  avgCostTry: number;
  costBasisTry: number;
  pnlTry: number;
  pnlPercent: number;
  targetWeight: number;
  currentWeight: number;
  weightDeviation: number;
}

export type AssetType = 'CRYPTO' | 'STOCK' | 'GOLD' | 'FUND' | 'CURRENCY' | 'OTHER';
export type TxnType = 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW' | 'REBALANCE' | 'BES_CONTRIBUTION';
```

## Formatters

```typescript
// src/utils/formatters.ts

// Turkish number format: ₺45.000,50
export function formatTRY(amount: number, showCents = false): string {
  return new Intl.NumberFormat('tr-TR', {
    style: 'currency',
    currency: 'TRY',
    minimumFractionDigits: showCents ? 2 : 0,
    maximumFractionDigits: showCents ? 2 : 0,
  }).format(amount);
}

// Percentage: %5,07 or -%4,50
export function formatPercent(value: number, decimals = 2): string {
  const formatted = Math.abs(value * 100).toFixed(decimals).replace('.', ',');
  return `${value < 0 ? '-' : ''}%${formatted}`;
}

// Date: "8 Nisan 2026"
export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat('tr-TR', {
    day: 'numeric', month: 'long', year: 'numeric'
  }).format(new Date(date));
}

// Month: "Nisan 2026"
export function formatMonth(period: string): string {
  const [year, month] = period.split('-');
  return new Intl.DateTimeFormat('tr-TR', { month: 'long', year: 'numeric' })
    .format(new Date(Number(year), Number(month) - 1));
}
```

## Skeleton Loading Pattern

```typescript
// Use skeleton for initial loads — never spinners
function PortfolioSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-8 w-48" />
      <div className="grid grid-cols-3 gap-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-24 rounded-lg" />
        ))}
      </div>
      <Skeleton className="h-64" />
    </div>
  );
}
```

## Empty State Pattern

```typescript
function EmptyState({ message, action }: { message: string; action?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <p className="text-muted-foreground mb-4">{message}</p>
      {action}
    </div>
  );
}
// Usage:
<EmptyState
  message="Henüz işlem yok"
  action={<AddTransactionDialog portfolioId={id} />}
/>
```

## Rules (don't violate these)

1. No `any` in TypeScript — ever
2. Currency numbers always use `formatTRY()` — never raw `.toFixed(2)`
3. All API calls through `src/api/client.ts` — never raw `fetch` or direct `axios`
4. All UI primitives from shadcn/ui — never raw `<input>`, `<button>`, `<select>`
5. Loading state = skeleton, not spinner
6. Error state = friendly Turkish message
7. Empty state = message + CTA action
8. `cn()` from `@/lib/utils` for conditional classNames
