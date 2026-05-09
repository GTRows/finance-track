import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { PageHeader } from '@/components/layout/PageHeader';
import { AccountPicker } from '@/components/accounts/AccountPicker';
import { useBankCsvCommit, useBankCsvPreview } from '@/hooks/useBankCsvImport';
import type { Bank, BankCsvImportSummary } from '@/types/bankCsv.types';
import {
  AlertTriangle,
  CheckCircle2,
  FileText,
  Loader2,
  Upload,
  Copy,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatCurrency, formatShortDate } from '@/utils/formatters';
import { useAccounts } from '@/hooks/useAccounts';

const BANK_OPTIONS: Bank[] = ['GARANTI', 'ISBANK', 'AKBANK'];

interface ApiErrorPayload {
  code?: string;
  message?: string;
}

interface AxiosLikeError {
  response?: { data?: ApiErrorPayload };
  message?: string;
}

function readErrorCode(err: unknown): string | null {
  const e = err as AxiosLikeError;
  return e?.response?.data?.code ?? null;
}

function readErrorMessage(err: unknown, fallback: string): string {
  const e = err as AxiosLikeError;
  return e?.response?.data?.message ?? e?.message ?? fallback;
}

export function BankCsvImportPage() {
  const { t } = useTranslation();
  const fileRef = useRef<HTMLInputElement>(null);
  const { data: accounts = [] } = useAccounts();

  const [bank, setBank] = useState<Bank>('GARANTI');
  const [accountId, setAccountId] = useState<string | undefined>(undefined);
  const [file, setFile] = useState<File | null>(null);
  const [summary, setSummary] = useState<BankCsvImportSummary | null>(null);
  const [committed, setCommitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const previewMutation = useBankCsvPreview();
  const commitMutation = useBankCsvCommit();
  const busy = previewMutation.isPending || commitMutation.isPending;
  const ready = !!file && !!accountId;

  const selectedAccount = useMemo(
    () => accounts.find((a) => a.id === accountId),
    [accounts, accountId],
  );

  const resolveErrorCopy = (err: unknown, fallbackKey: string): string => {
    const code = readErrorCode(err);
    if (code) {
      const key = `bankCsv.errors.${code}`;
      const translated = t(key);
      if (translated !== key) return translated;
    }
    return readErrorMessage(err, t(fallbackKey));
  };

  const resetSummary = () => {
    setSummary(null);
    setCommitted(false);
    setError(null);
    setConfirming(false);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null;
    setFile(f);
    resetSummary();
  };

  const handleBankChange = (next: Bank) => {
    setBank(next);
    resetSummary();
  };

  const handleAccountChange = (next: string | undefined) => {
    setAccountId(next);
    resetSummary();
  };

  const handlePreview = async () => {
    if (!file || !accountId) return;
    setError(null);
    setCommitted(false);
    try {
      const result = await previewMutation.mutateAsync({ file, bank, accountId });
      setSummary(result);
    } catch (err) {
      setSummary(null);
      setError(resolveErrorCopy(err, 'bankCsv.previewFailed'));
    }
  };

  const handleCommitClick = () => {
    if (!summary || summary.totalRows === 0) return;
    setConfirming(true);
  };

  const handleCommitConfirm = async () => {
    setConfirming(false);
    if (!file || !accountId) return;
    setError(null);
    try {
      const result = await commitMutation.mutateAsync({ file, bank, accountId });
      setSummary(result);
      setCommitted(true);
    } catch (err) {
      setError(resolveErrorCopy(err, 'bankCsv.commitFailed'));
    }
  };

  const importableRows = summary
    ? summary.totalRows - summary.duplicateRows - summary.warningRows
    : 0;

  return (
    <div className="space-y-6 max-w-[1200px]">
      <PageHeader title={t('bankCsv.title')} description={t('bankCsv.subtitle')} />

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">{t('bankCsv.setupTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          {/* Bank radio group */}
          <div className="space-y-1.5">
            <Label>{t('bankCsv.bank')}</Label>
            <div
              className="grid grid-cols-1 sm:grid-cols-3 gap-2"
              role="radiogroup"
              aria-label={t('bankCsv.bank')}
            >
              {BANK_OPTIONS.map((option) => {
                const active = option === bank;
                return (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    onClick={() => handleBankChange(option)}
                    className={cn(
                      'flex items-center justify-between rounded-md border px-3 h-10 text-sm transition-colors cursor-pointer',
                      active
                        ? 'border-primary bg-primary/10 text-primary'
                        : 'border-input bg-background text-foreground hover:bg-accent',
                    )}
                  >
                    <span className="font-medium">{t(`bankCsv.banks.${option}`)}</span>
                    {active && <CheckCircle2 className="w-4 h-4" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Account picker */}
          <AccountPicker
            value={accountId}
            onChange={handleAccountChange}
            label={t('bankCsv.account')}
          />

          {/* File picker */}
          <div className="space-y-1.5">
            <Label>{t('bankCsv.file')}</Label>
            <div className="flex items-center gap-3 flex-wrap">
              <input
                ref={fileRef}
                type="file"
                accept=".csv,text/csv"
                className="hidden"
                onChange={handleFileChange}
              />
              <Button
                variant="outline"
                size="sm"
                onClick={() => fileRef.current?.click()}
                className="cursor-pointer"
                type="button"
              >
                <FileText className="w-3.5 h-3.5 mr-1.5" />
                {t('bankCsv.chooseFile')}
              </Button>
              {file && <span className="text-xs text-muted-foreground">{file.name}</span>}
            </div>
          </div>

          {/* Action row */}
          <div className="flex items-center gap-2 flex-wrap pt-2">
            <Button
              size="sm"
              variant="outline"
              onClick={handlePreview}
              disabled={!ready || busy}
              className="cursor-pointer"
              type="button"
            >
              {previewMutation.isPending ? (
                <Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />
              ) : null}
              {t('bankCsv.preview')}
            </Button>
            <Button
              size="sm"
              onClick={handleCommitClick}
              disabled={!summary || summary.totalRows === 0 || busy || committed}
              className="cursor-pointer"
              type="button"
            >
              {commitMutation.isPending ? (
                <Loader2 className="w-3.5 h-3.5 mr-1.5 animate-spin" />
              ) : (
                <Upload className="w-3.5 h-3.5 mr-1.5" />
              )}
              {t('bankCsv.commit')}
            </Button>
          </div>

          {confirming && summary && (
            <div className="flex items-start gap-3 rounded-md border border-primary/30 bg-primary/5 p-3 text-xs">
              <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0 text-primary" />
              <div className="flex-1 space-y-2">
                <p>
                  {t('bankCsv.confirm', {
                    count: importableRows,
                    bank: t(`bankCsv.banks.${bank}`),
                    account: selectedAccount?.name ?? '--',
                  })}
                </p>
                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    onClick={handleCommitConfirm}
                    className="cursor-pointer"
                    type="button"
                  >
                    {t('bankCsv.confirmYes')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setConfirming(false)}
                    className="cursor-pointer"
                    type="button"
                  >
                    {t('bankCsv.confirmNo')}
                  </Button>
                </div>
              </div>
            </div>
          )}

          {error && (
            <div className="flex items-start gap-2 rounded-md border border-rose-500/30 bg-rose-500/5 p-3 text-xs text-rose-400">
              <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <span className="flex-1">{error}</span>
            </div>
          )}

          {committed && summary && (
            <div className="flex items-start gap-2 rounded-md border border-emerald-500/30 bg-emerald-500/5 p-3 text-xs text-emerald-400">
              <CheckCircle2 className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <span>
                {t('bankCsv.commitDone', {
                  imported: summary.importedRows,
                  duplicates: summary.duplicateRows,
                  skipped: summary.skippedRows,
                })}
              </span>
            </div>
          )}
        </CardContent>
      </Card>

      {summary && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">{t('bankCsv.summaryTitle')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
              <Stat label={t('bankCsv.totalRows')} value={summary.totalRows} />
              <Stat
                label={t('bankCsv.importedRows')}
                value={summary.importedRows}
                tone="positive"
              />
              <Stat
                label={t('bankCsv.skippedRows')}
                value={summary.skippedRows}
                tone="muted"
              />
              <Stat
                label={t('bankCsv.duplicateRows')}
                value={summary.duplicateRows}
                tone="muted"
              />
              <Stat
                label={t('bankCsv.warningRows')}
                value={summary.warningRows}
                tone="warning"
              />
            </div>

            {summary.rows.length === 0 ? (
              <div className="text-xs text-muted-foreground py-6 text-center">
                {t('bankCsv.empty')}
              </div>
            ) : (
              <div className="border rounded-lg max-h-[28rem] overflow-auto">
                <table className="w-full text-xs">
                  <thead className="bg-muted/50 sticky top-0 z-10">
                    <tr className="text-left">
                      <th className="px-2 py-1.5 font-medium">#</th>
                      <th className="px-2 py-1.5 font-medium">{t('bankCsv.col.date')}</th>
                      <th className="px-2 py-1.5 font-medium">{t('bankCsv.col.type')}</th>
                      <th className="px-2 py-1.5 font-medium text-right">
                        {t('bankCsv.col.amount')}
                      </th>
                      <th className="px-2 py-1.5 font-medium">{t('bankCsv.col.description')}</th>
                      <th className="px-2 py-1.5 font-medium">{t('bankCsv.col.category')}</th>
                      <th className="px-2 py-1.5 font-medium">{t('bankCsv.col.status')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {summary.rows.map((r) => {
                      const amount = r.amount ? Number(r.amount) : null;
                      const rowKey = `${r.rowNumber}-${r.fingerprint ?? 'na'}`;
                      const tone = r.warning
                        ? 'bg-amber-500/5'
                        : r.duplicate
                          ? 'bg-muted/30'
                          : '';
                      return (
                        <tr key={rowKey} className={cn('border-t', tone)}>
                          <td className="px-2 py-1.5 text-muted-foreground tabular-nums">
                            {r.rowNumber}
                          </td>
                          <td className="px-2 py-1.5">
                            {r.date ? formatShortDate(r.date) : '--'}
                          </td>
                          <td className="px-2 py-1.5">
                            {r.inferredType === 'INCOME' ? (
                              <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-emerald-500">
                                {t('bankCsv.type.INCOME')}
                              </span>
                            ) : r.inferredType === 'EXPENSE' ? (
                              <span className="inline-flex items-center rounded-full bg-rose-500/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-rose-500">
                                {t('bankCsv.type.EXPENSE')}
                              </span>
                            ) : (
                              <span className="text-muted-foreground">--</span>
                            )}
                          </td>
                          <td className="px-2 py-1.5 text-right tabular-nums font-mono">
                            {amount != null && Number.isFinite(amount)
                              ? formatCurrency(
                                  amount,
                                  true,
                                  selectedAccount?.currency ?? undefined,
                                )
                              : '--'}
                          </td>
                          <td className="px-2 py-1.5">
                            <span
                              className="block max-w-[18rem] truncate"
                              title={r.description ?? ''}
                            >
                              {r.description ?? '--'}
                            </span>
                          </td>
                          <td className="px-2 py-1.5 text-muted-foreground">
                            {r.matchedCategoryName ?? '--'}
                          </td>
                          <td className="px-2 py-1.5">
                            {r.warning ? (
                              <span className="inline-flex items-center gap-1 text-amber-500">
                                <AlertTriangle className="w-3 h-3" />
                                {r.warning}
                              </span>
                            ) : r.duplicate ? (
                              <span className="inline-flex items-center gap-1 text-muted-foreground">
                                <Copy className="w-3 h-3" />
                                {t('bankCsv.row.duplicate')}
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-emerald-500">
                                <CheckCircle2 className="w-3 h-3" />
                                {t('bankCsv.row.ok')}
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone?: 'positive' | 'muted' | 'warning';
}) {
  const color =
    tone === 'positive'
      ? 'text-emerald-500'
      : tone === 'warning'
        ? 'text-amber-500'
        : tone === 'muted'
          ? 'text-muted-foreground'
          : 'text-foreground';
  return (
    <div className="rounded-md border bg-card p-2.5">
      <div className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className={cn('text-lg font-semibold tabular-nums', color)}>{value}</div>
    </div>
  );
}
