export type DebtStatus = 'ACTIVE' | 'PAID_OFF';

export type DebtType =
  | 'MORTGAGE'
  | 'AUTO'
  | 'PERSONAL'
  | 'CREDIT_CARD'
  | 'STUDENT'
  | 'OTHER';

export interface AmortizationRow {
  dueDate: string;
  payment: number;
  principal: number;
  interest: number;
  remainingBalance: number;
}

export interface Debt {
  id: string;
  name: string;
  debtType: DebtType;
  principal: number;
  annualRate: number;
  termMonths: number;
  startDate: string;
  notes: string | null;
  scheduledMonthlyPayment: number;
  totalScheduledPaid: number;
  totalActuallyPaid: number;
  remainingBalance: number;
  totalInterest: number;
  scheduledPayoffDate: string;
  projectedPayoffDate: string | null;
  progressRatio: number;
  monthsAhead: number;
  status: DebtStatus;
  nextPayments: AmortizationRow[];
}

export interface DebtPayment {
  id: string;
  paymentDate: string;
  amount: number;
  note: string | null;
}

export interface UpsertDebtRequest {
  name: string;
  debtType: DebtType;
  principal: number;
  annualRate: number;
  termMonths: number;
  startDate: string;
  notes?: string | null;
}

export interface DebtPaymentRequest {
  paymentDate: string;
  amount: number;
  note?: string | null;
}
