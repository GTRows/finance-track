/** Bill payment status. */
export type PaymentStatus = 'PENDING' | 'PAID' | 'SKIPPED';

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
}

/** Bill payment history entry. */
export interface BillPayment {
  period: string;
  amount: number | null;
  status: PaymentStatus;
  paidAt: string | null;
}
