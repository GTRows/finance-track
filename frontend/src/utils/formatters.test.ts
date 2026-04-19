import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import i18n from '@/i18n/config';
import { useSettingsStore } from '@/store/settings.store';
import {
  formatCurrency,
  formatDate,
  formatMonth,
  formatPercent,
  formatShortDate,
} from './formatters';

function stripNbsp(s: string): string {
  return s.replace(/\u00A0/g, ' ').replace(/\u202F/g, ' ').trim();
}

describe('formatters', () => {
  beforeEach(() => {
    useSettingsStore.setState({
      settings: {
        currency: 'TRY',
        language: 'en',
        theme: 'dark',
        timezone: 'Europe/Istanbul',
      },
    });
  });

  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  describe('formatCurrency', () => {
    it('uses the store currency when none is passed', async () => {
      await i18n.changeLanguage('en');
      const out = stripNbsp(formatCurrency(1234));
      expect(out).toContain('TRY');
      expect(out).toMatch(/1[,.]?234/);
    });

    it('honors the override currency argument', async () => {
      await i18n.changeLanguage('en');
      const out = stripNbsp(formatCurrency(50, true, 'USD'));
      expect(out).toContain('$');
      expect(out).toContain('50.00');
    });

    it('respects showCents flag', async () => {
      await i18n.changeLanguage('en');
      const zeroDecimal = stripNbsp(formatCurrency(100, false, 'USD'));
      const twoDecimal = stripNbsp(formatCurrency(100, true, 'USD'));
      expect(zeroDecimal).not.toContain('.');
      expect(twoDecimal).toContain('100.00');
    });

    it('switches grouping style when language changes to tr', async () => {
      await i18n.changeLanguage('tr');
      const out = stripNbsp(formatCurrency(1234567, false, 'USD'));
      expect(out).toMatch(/1\.234\.567/);
    });
  });

  describe('formatPercent', () => {
    it('formats decimal as percent with fixed precision', async () => {
      await i18n.changeLanguage('en');
      expect(stripNbsp(formatPercent(0.1234))).toBe('12.34%');
    });

    it('defaults to two decimals', async () => {
      await i18n.changeLanguage('en');
      expect(stripNbsp(formatPercent(0.5))).toBe('50.00%');
    });

    it('accepts custom precision', async () => {
      await i18n.changeLanguage('en');
      expect(stripNbsp(formatPercent(0.125, 1))).toBe('12.5%');
    });
  });

  describe('formatDate variants', () => {
    it('formatDate produces a long-form date in the configured locale', async () => {
      await i18n.changeLanguage('en');
      const out = formatDate('2026-04-19');
      expect(out).toMatch(/April/);
      expect(out).toContain('2026');
    });

    it('formatMonth produces month + year for YYYY-MM period', async () => {
      await i18n.changeLanguage('en');
      const out = formatMonth('2026-04');
      expect(out).toMatch(/April/);
      expect(out).toContain('2026');
    });

    it('formatShortDate uses numeric day and month', async () => {
      await i18n.changeLanguage('en');
      const out = formatShortDate('2026-04-19');
      expect(out).toMatch(/\d{2}\/\d{2}\/2026/);
    });
  });
});
