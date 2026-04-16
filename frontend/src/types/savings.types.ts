export type SavingsGoalStatus = 'IN_PROGRESS' | 'REACHED';

export interface SavingsGoal {
  id: string;
  name: string;
  targetAmount: number;
  targetDate: string | null;
  linkedPortfolioId: string | null;
  linkedPortfolioName: string | null;
  notes: string | null;
  currentAmount: number;
  progressRatio: number;
  monthlyPace: number | null;
  projectedCompletion: string | null;
  status: SavingsGoalStatus;
}

export interface SavingsContribution {
  id: string;
  contributionDate: string;
  amount: number;
  note: string | null;
}

export interface UpsertSavingsGoalRequest {
  name: string;
  targetAmount: number;
  targetDate?: string | null;
  linkedPortfolioId?: string | null;
  notes?: string | null;
}

export interface SavingsContributionRequest {
  contributionDate: string;
  amount: number;
  note?: string | null;
}
