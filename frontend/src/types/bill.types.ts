/** Bill payment status. */
export type PaymentStatus = 'PENDING' | 'PAID' | 'SKIPPED';

/** Month-over-month variance between the two most recent paid periods. */
export interface BillVariance {
  currentPeriod: string;
  currentAmount: number;
  previousPeriod: string;
  previousAmount: number;
  delta: number;
  deltaPercent: number;
  flagged: boolean;
}

/** Recurring bill. */
export interface Bill {
  id: string;
  name: string;
  amount: number;
  dueDay: number;
  isActive: boolean;
  category: string;
  remindDaysBefore: number;
  currentPeriodStatus: PaymentStatus;
  currentPeriodDueDate: string;
  daysUntilDue: number;
  lastUsedOn: string | null;
  daysSinceLastUse: number | null;
  variance: BillVariance | null;
}

/** Reason a bill was flagged in the subscription audit. */
export type SubscriptionAuditReason = 'NEVER_USED' | 'STALE';

/** One flagged subscription. */
export interface SubscriptionAuditCandidate {
  billId: string;
  name: string;
  category: string | null;
  amount: number;
  currency: string;
  lastUsedOn: string | null;
  daysSinceLastUse: number | null;
  reason: SubscriptionAuditReason;
}

/** Subscription audit summary returned by GET /bills/audit. */
export interface SubscriptionAudit {
  totalMonthlySpend: number;
  potentialMonthlySavings: number;
  candidateCount: number;
  candidates: SubscriptionAuditCandidate[];
}

/** Bill payment history entry. */
export interface BillPayment {
  period: string;
  amount: number | null;
  status: PaymentStatus;
  paidAt: string | null;
  notes: string | null;
}

/** Request body for creating/updating a bill. */
export interface CreateBillRequest {
  name: string;
  amount: number;
  dueDay: number;
  category?: string;
  remindDaysBefore: number;
  autoPay: boolean;
  notes?: string;
}

/** Request body for marking a bill as paid. */
export interface PayBillRequest {
  period: string;
  amount?: number;
  notes?: string;
}
