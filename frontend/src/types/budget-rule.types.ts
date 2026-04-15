export interface BudgetRule {
  id: string;
  categoryId: string;
  categoryName: string;
  categoryColor: string | null;
  monthlyLimitTry: number;
  currentSpendTry: number;
  usagePct: number;
  active: boolean;
  lastAlertedPeriod: string | null;
  createdAt: string;
}

export interface CreateBudgetRuleRequest {
  categoryId: string;
  monthlyLimitTry: number;
}
