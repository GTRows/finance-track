/**
 * Formats a number as Turkish Lira currency.
 * Uses Turkish locale: dots for thousands, comma for decimals.
 * Example: formatTRY(45000.50) -> "45.000,50"
 */
export function formatTRY(amount: number, showCents = false): string {
  return new Intl.NumberFormat('tr-TR', {
    style: 'currency',
    currency: 'TRY',
    minimumFractionDigits: showCents ? 2 : 0,
    maximumFractionDigits: showCents ? 2 : 0,
  }).format(amount);
}

/**
 * Formats a decimal value as a Turkish-style percentage.
 * Example: formatPercent(0.0507) -> "%5,07"
 */
export function formatPercent(value: number, decimals = 2): string {
  const formatted = Math.abs(value * 100).toFixed(decimals).replace('.', ',');
  return `${value < 0 ? '-' : ''}%${formatted}`;
}

/**
 * Formats a date string as a long Turkish date.
 * Example: formatDate("2026-04-08") -> "8 Nisan 2026"
 */
export function formatDate(date: string | Date): string {
  return new Intl.DateTimeFormat('tr-TR', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(date));
}

/**
 * Formats a YYYY-MM period as a Turkish month name.
 * Example: formatMonth("2026-04") -> "Nisan 2026"
 */
export function formatMonth(period: string): string {
  const [year, month] = period.split('-');
  return new Intl.DateTimeFormat('tr-TR', { month: 'long', year: 'numeric' }).format(
    new Date(Number(year), Number(month) - 1)
  );
}

/**
 * Formats a short date.
 * Example: formatShortDate("2026-04-08") -> "08.04.2026"
 */
export function formatShortDate(date: string | Date): string {
  return new Intl.DateTimeFormat('tr-TR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(date));
}
