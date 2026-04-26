import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Upload, FileSpreadsheet, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import client from '@/api/client';
import { formatCurrency, formatShortDate } from '@/utils/formatters';

interface ImportPreviewRow {
  rowNumber: number;
  date: string | null;
  rawType: string | null;
  mappedType: string | null;
  assetSymbol: string | null;
  amountTry: number | null;
  note: string | null;
  warning: string | null;
}

interface ImportSummary {
  totalRows: number;
  importedRows: number;
  skippedRows: number;
  warningRows: number;
  rows: ImportPreviewRow[];
}

export function ImportExcelSection() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [summary, setSummary] = useState<ImportSummary | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [committed, setCommitted] = useState(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    setFile(f ?? null);
    setSummary(null);
    setCommitted(false);
    setError(null);
  };

  const upload = async (path: string): Promise<ImportSummary> => {
    if (!file) throw new Error(t('settings.importNoFile'));
    const fd = new FormData();
    fd.append('file', file);
    const { data } = await client.post<ImportSummary>(path, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  };

  const handlePreview = async () => {
    setBusy(true);
    setError(null);
    try {
      setSummary(await upload('/import/excel/preview'));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('settings.importPreviewFailed'));
    } finally {
      setBusy(false);
    }
  };

  const handleCommit = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await upload('/import/excel/commit');
      setSummary(result);
      setCommitted(true);
      void qc.invalidateQueries({ queryKey: ['portfolios'] });
      void qc.invalidateQueries({ queryKey: ['transactions'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
    } catch (e) {
      setError(e instanceof Error ? e.message : t('settings.importFailed'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">{t('settings.importDesc')}</p>

      <div className="flex items-center gap-3 flex-wrap">
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx"
          className="hidden"
          onChange={handleFileChange}
        />
        <Button
          variant="outline"
          size="sm"
          onClick={() => fileRef.current?.click()}
          className="cursor-pointer"
        >
          <FileSpreadsheet className="w-3.5 h-3.5 mr-1.5" />
          {t('settings.importChoose')}
        </Button>
        {file && <span className="text-xs text-muted-foreground">{file.name}</span>}
        <Button
          size="sm"
          variant="outline"
          onClick={handlePreview}
          disabled={!file || busy}
          className="cursor-pointer"
        >
          {busy ? t('common.loading') : t('settings.importPreview')}
        </Button>
        <Button
          size="sm"
          onClick={handleCommit}
          disabled={!file || busy || !summary}
          className="cursor-pointer"
        >
          <Upload className="w-3.5 h-3.5 mr-1.5" />
          {busy ? t('common.loading') : t('settings.importRun')}
        </Button>
      </div>

      {error && (
        <div className="flex items-start gap-2 rounded-md border border-rose-500/30 bg-rose-500/5 p-3 text-xs text-rose-400">
          <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
          {error}
        </div>
      )}

      {summary && (
        <div className="space-y-3">
          {committed && (
            <div className="flex items-start gap-2 rounded-md border border-emerald-500/30 bg-emerald-500/5 p-3 text-xs text-emerald-400">
              <CheckCircle2 className="w-4 h-4 mt-0.5 flex-shrink-0" />
              {t('settings.importDone', {
                imported: summary.importedRows,
                skipped: summary.skippedRows,
              })}
            </div>
          )}

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Stat label={t('settings.importTotal')} value={summary.totalRows} />
            <Stat label={t('settings.importImported')} value={summary.importedRows} tone="positive" />
            <Stat label={t('settings.importSkipped')} value={summary.skippedRows} tone="muted" />
            <Stat label={t('settings.importWarnings')} value={summary.warningRows} tone="warning" />
          </div>

          <div className="border rounded-lg max-h-80 overflow-auto">
            <table className="w-full text-xs">
              <thead className="bg-muted/50 sticky top-0">
                <tr className="text-left">
                  <th className="px-2 py-1.5 font-medium">#</th>
                  <th className="px-2 py-1.5 font-medium">{t('settings.importColDate')}</th>
                  <th className="px-2 py-1.5 font-medium">{t('settings.importColType')}</th>
                  <th className="px-2 py-1.5 font-medium">{t('settings.importColAsset')}</th>
                  <th className="px-2 py-1.5 font-medium text-right">{t('settings.importColAmount')}</th>
                  <th className="px-2 py-1.5 font-medium">{t('settings.importColStatus')}</th>
                </tr>
              </thead>
              <tbody>
                {summary.rows.map((r) => (
                  <tr
                    key={r.rowNumber}
                    className={cn(
                      'border-t',
                      r.warning ? 'bg-amber-500/5' : 'bg-transparent'
                    )}
                  >
                    <td className="px-2 py-1.5 text-muted-foreground tabular-nums">{r.rowNumber}</td>
                    <td className="px-2 py-1.5">{r.date ? formatShortDate(r.date) : '--'}</td>
                    <td className="px-2 py-1.5 text-muted-foreground">{r.rawType ?? '--'}</td>
                    <td className="px-2 py-1.5 font-medium">{r.assetSymbol ?? '--'}</td>
                    <td className="px-2 py-1.5 text-right tabular-nums font-mono">
                      {r.amountTry != null ? formatCurrency(r.amountTry) : '--'}
                    </td>
                    <td className="px-2 py-1.5">
                      {r.warning ? (
                        <span className="text-amber-500">{r.warning}</span>
                      ) : (
                        <span className="text-emerald-500">{r.mappedType}</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
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
