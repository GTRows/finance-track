import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Paperclip, FileText, Loader2, X } from 'lucide-react';
import { receiptApi } from '@/api/receipt.api';

interface Props {
  transactionId: string;
  hasReceipt: boolean;
  month: string;
}

const ACCEPT = 'image/jpeg,image/png,image/webp,application/pdf';

export function ReceiptAction({ transactionId, hasReceipt, month }: Props) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [busy, setBusy] = useState<null | 'upload' | 'remove' | 'view'>(null);

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['budget', 'transactions', month] });
  };

  const handleFile = async (file: File) => {
    try {
      setBusy('upload');
      await receiptApi.upload(transactionId, file);
      invalidate();
    } catch {
      // silent — backend surface
    } finally {
      setBusy(null);
    }
  };

  const handleView = async () => {
    try {
      setBusy('view');
      const blob = await receiptApi.download(transactionId);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      // silent
    } finally {
      setBusy(null);
    }
  };

  const handleRemove = async () => {
    try {
      setBusy('remove');
      await receiptApi.remove(transactionId);
      invalidate();
    } catch {
      // silent
    } finally {
      setBusy(null);
    }
  };

  if (!hasReceipt) {
    return (
      <>
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPT}
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void handleFile(file);
            e.target.value = '';
          }}
        />
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={busy !== null}
          title={t('budget.receipt.attach')}
          className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded hover:bg-accent cursor-pointer disabled:opacity-50"
        >
          {busy === 'upload' ? (
            <Loader2 className="w-3.5 h-3.5 animate-spin text-muted-foreground" />
          ) : (
            <Paperclip className="w-3.5 h-3.5 text-muted-foreground" />
          )}
        </button>
      </>
    );
  }

  return (
    <div className="flex items-center gap-0.5">
      <button
        type="button"
        onClick={handleView}
        disabled={busy !== null}
        title={t('budget.receipt.view')}
        className="p-1 rounded hover:bg-sky-500/10 cursor-pointer disabled:opacity-50"
      >
        {busy === 'view' ? (
          <Loader2 className="w-3.5 h-3.5 animate-spin text-sky-400" />
        ) : (
          <FileText className="w-3.5 h-3.5 text-sky-400" />
        )}
      </button>
      <button
        type="button"
        onClick={handleRemove}
        disabled={busy !== null}
        title={t('budget.receipt.remove')}
        className="opacity-0 group-hover:opacity-100 transition-opacity p-1 rounded hover:bg-destructive/10 cursor-pointer disabled:opacity-50"
      >
        {busy === 'remove' ? (
          <Loader2 className="w-3.5 h-3.5 animate-spin text-destructive" />
        ) : (
          <X className="w-3.5 h-3.5 text-destructive" />
        )}
      </button>
    </div>
  );
}
