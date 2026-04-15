import i18n from '@/i18n/config';
import { useSettingsStore } from '@/store/settings.store';

function currentLocale(): string {
  const lang = i18n.resolvedLanguage ?? i18n.language ?? 'en';
  return lang === 'tr' ? 'tr-TR' : 'en-US';
}

function currentCurrency(): string {
  return useSettingsStore.getState().settings.currency || 'TRY';
}

function currentTimezone(): string {
  return useSettingsStore.getState().settings.timezone || 'Europe/Istanbul';
}

export function formatCurrency(amount: number, showCents = false, currency?: string): string {
  return new Intl.NumberFormat(currentLocale(), {
    style: 'currency',
    currency: currency ?? currentCurrency(),
    minimumFractionDigits: showCents ? 2 : 0,
    maximumFractionDigits: showCents ? 2 : 0,
  }).format(amount);
}

export const formatTRY = formatCurrency;

export function formatPercent(value: number, decimals = 2): string {
  return new Intl.NumberFormat(currentLocale(), {
    style: 'percent',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}

export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat(currentLocale(), {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: currentTimezone(),
  }).format(new Date(date));
}

export function formatMonth(period: string): string {
  const [year, month] = period.split('-');
  return new Intl.DateTimeFormat(currentLocale(), {
    month: 'long',
    year: 'numeric',
  }).format(new Date(Number(year), Number(month) - 1));
}

export function formatShortDate(date: string | Date): string {
  return new Intl.DateTimeFormat(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: currentTimezone(),
  }).format(new Date(date));
}

export function formatDateTime(date: string | Date): string {
  return new Intl.DateTimeFormat(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: currentTimezone(),
  }).format(new Date(date));
}
