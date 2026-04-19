import { describe, expect, it } from 'vitest';
import {
  calculatePnl,
  calculatePnlPercent,
  calculateSavingsRate,
  calculateWeight,
} from './calculations';

describe('calculatePnl', () => {
  it('returns positive delta when market value exceeds cost basis', () => {
    expect(calculatePnl(1500, 1000)).toBe(500);
  });

  it('returns negative delta when cost basis exceeds market value', () => {
    expect(calculatePnl(800, 1000)).toBe(-200);
  });

  it('returns zero when values match', () => {
    expect(calculatePnl(1000, 1000)).toBe(0);
  });
});

describe('calculatePnlPercent', () => {
  it('returns fractional gain for positive movement', () => {
    expect(calculatePnlPercent(1500, 1000)).toBeCloseTo(0.5, 10);
  });

  it('returns fractional loss for negative movement', () => {
    expect(calculatePnlPercent(800, 1000)).toBeCloseTo(-0.2, 10);
  });

  it('returns zero when cost basis is zero to avoid divide-by-zero', () => {
    expect(calculatePnlPercent(1500, 0)).toBe(0);
  });
});

describe('calculateWeight', () => {
  it('returns holding share of portfolio as decimal', () => {
    expect(calculateWeight(250, 1000)).toBeCloseTo(0.25, 10);
  });

  it('returns zero when portfolio is empty', () => {
    expect(calculateWeight(250, 0)).toBe(0);
  });
});

describe('calculateSavingsRate', () => {
  it('returns percent of income retained', () => {
    expect(calculateSavingsRate(10000, 4111)).toBeCloseTo(58.89, 2);
  });

  it('returns negative rate when spending exceeds income', () => {
    expect(calculateSavingsRate(1000, 1500)).toBe(-50);
  });

  it('returns zero when income is zero', () => {
    expect(calculateSavingsRate(0, 500)).toBe(0);
  });
});
