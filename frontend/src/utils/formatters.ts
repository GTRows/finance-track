import i18n from '@/i18n/config';

function currentLocale(): string {
  const lang = i18n.resolvedLanguage ?? i18n.language ?? 'en';
  return lang === 'tr' ? 'tr-TR' : 'en-US';
}

/**
 * Formats a number as Turkish Lira currency using the active UI locale.
 * Turkish locale: "45.000,50 ₺". English locale: "₺45,000.50".
 */
export function formatTRY(amount: number, showCents = false): string {
  return new Intl.NumberFormat(currentLocale(), {
    style: 'currency',
    currency: 'TRY',
    minimumFractionDigits: showCents ? 2 : 0,
    maximumFractionDigits: showCents ? 2 : 0,
  }).format(amount);
}

/**
 * Formats a decimal value as a percentage using the active UI locale.
 * Example (tr): formatPercent(0.0507) -> "%5,07"
 */
export function formatPercent(value: number, decimals = 2): string {
  const locale = currentLocale();
  const formatted = new Intl.NumberFormat(locale, {
    style: 'percent',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
  return formatted;
}

/** Formats a date as a long, locale-aware date. */
export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat(currentLocale(), {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(date));
}

/** Formats a YYYY-MM period as a locale-aware month + year. */
export function formatMonth(period: string): string {
  const [year, month] = period.split('-');
  return new Intl.DateTimeFormat(currentLocale(), {
    month: 'long',
    year: 'numeric',
  }).format(new Date(Number(year), Number(month) - 1));
}

/** Formats a short numeric date using the active UI locale. */
export function formatShortDate(date: string | Date): string {
  return new Intl.DateTimeFormat(currentLocale(), {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(date));
}
