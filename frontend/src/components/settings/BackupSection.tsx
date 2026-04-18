import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Download, Upload, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { downloadBackup, uploadBackup } from '@/api/backup.api';

export function BackupSection() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<'export' | 'import' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [confirming, setConfirming] = useState(false);

  const handleExport = async () => {
    setBusy('export');
    setError(null);
    setDone(null);
    try {
      const { blob, filename } = await downloadBackup();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      setDone(t('settings.backupExportDone'));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('settings.backupExportFailed'));
    } finally {
      setBusy(null);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setPendingFile(f);
    setConfirming(true);
    setError(null);
    setDone(null);
    e.target.value = '';
  };

  const handleConfirmRestore = async () => {
    if (!pendingFile) return;
    setBusy('import');
    setError(null);
    try {
      const text = await pendingFile.text();
      const payload = JSON.parse(text);
      const result = await uploadBackup(payload);
      setDone(
        t('settings.backupImportDone', {
          tx: result.transactions,
          portfolios: result.portfolios,
          bills: result.bills,
        })
      );
      qc.invalidateQueries();
    } catch (e) {
      setError(e instanceof Error ? e.message : t('settings.backupImportFailed'));
    } finally {
      setBusy(null);
      setConfirming(false);
      setPendingFile(null);
    }
  };

  const handleCancelRestore = () => {
    setConfirming(false);
    setPendingFile(null);
  };

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">{t('settings.backupDesc')}</p>

      <div className="flex items-center gap-3 flex-wrap">
        <Button
          variant="outline"
          size="sm"
          onClick={handleExport}
          disabled={busy !== null}
          className="cursor-pointer"
        >
          <Download className="w-3.5 h-3.5 mr-1.5" />
          {busy === 'export' ? t('common.loading') : t('settings.backupExport')}
        </Button>
        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          className="hidden"
          onChange={handleFileChange}
        />
        <Button
          variant="outline"
          size="sm"
          onClick={() => fileRef.current?.click()}
          disabled={busy !== null}
          className="cursor-pointer"
        >
          <Upload className="w-3.5 h-3.5 mr-1.5" />
          {t('settings.backupImport')}
        </Button>
      </div>

      {confirming && pendingFile && (
        <div className="rounded-md border border-amber-500/30 bg-amber-500/5 p-3 space-y-2.5">
          <div className="flex items-start gap-2 text-xs text-amber-400">
            <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
            <div>
              <div className="font-medium">{t('settings.backupImportConfirmTitle')}</div>
              <div className="mt-1 text-amber-400/80">
                {t('settings.backupImportConfirmBody', { name: pendingFile.name })}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="destructive"
              onClick={handleConfirmRestore}
              disabled={busy !== null}
              className="cursor-pointer"
            >
              {busy === 'import' ? t('common.loading') : t('settings.backupImportConfirm')}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={handleCancelRestore}
              disabled={busy !== null}
              className="cursor-pointer"
            >
              {t('common.cancel')}
            </Button>
          </div>
        </div>
      )}

      {error && (
        <div className="flex items-start gap-2 rounded-md border border-rose-500/30 bg-rose-500/5 p-3 text-xs text-rose-400">
          <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
          {error}
        </div>
      )}

      {done && !error && (
        <div className="flex items-start gap-2 rounded-md border border-emerald-500/30 bg-emerald-500/5 p-3 text-xs text-emerald-400">
          <CheckCircle2 className="w-4 h-4 mt-0.5 flex-shrink-0" />
          {done}
        </div>
      )}
    </div>
  );
}
