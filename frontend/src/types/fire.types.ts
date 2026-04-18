export interface FireTrajectoryPoint {
  year: number;
  date: string;
  netWorth: number;
}

export interface FireResult {
  currentNetWorth: number;
  avgMonthlyIncome: number;
  avgMonthlyExpense: number;
  savingsRate: number;
  monthlyContribution: number;
  withdrawalRate: number;
  expectedReturn: number;
  targetNumber: number;
  progressRatio: number;
  monthsToFi: number | null;
  yearsToFi: number | null;
  projectedFiDate: string | null;
  samplesUsed: number;
  sufficientData: boolean;
  trajectory: FireTrajectoryPoint[];
}

export interface FireQuery {
  withdrawalRate?: number;
  expectedReturn?: number;
  monthlyContribution?: number;
  monthlyExpense?: number;
  netWorth?: number;
}
