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
  variance: BillVariance | null;
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
