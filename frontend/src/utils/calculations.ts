/**
 * Calculates profit/loss in TRY.
 *
 * @param currentValue current market value
 * @param costBasis total cost invested
 * @returns absolute P&L amount
 */
export function calculatePnl(currentValue: number, costBasis: number): number {
  return currentValue - costBasis;
}

/**
 * Calculates profit/loss as a percentage.
 *
 * @param currentValue current market value
 * @param costBasis total cost invested
 * @returns P&L percentage (e.g., 0.15 for 15%)
 */
export function calculatePnlPercent(currentValue: number, costBasis: number): number {
  if (costBasis === 0) return 0;
  return (currentValue - costBasis) / costBasis;
}

/**
 * Calculates the allocation weight of a holding within a portfolio.
 *
 * @param holdingValue value of this holding
 * @param totalValue total portfolio value
 * @returns weight as a decimal (e.g., 0.45 for 45%)
 */
export function calculateWeight(holdingValue: number, totalValue: number): number {
  if (totalValue === 0) return 0;
  return holdingValue / totalValue;
}

/**
 * Calculates the savings rate.
 *
 * @param income total income
 * @param expense total expense
 * @returns savings rate as a percentage (e.g., 58.89)
 */
export function calculateSavingsRate(income: number, expense: number): number {
  if (income === 0) return 0;
  return ((income - expense) / income) * 100;
}
