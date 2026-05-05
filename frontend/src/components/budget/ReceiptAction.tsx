import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  AlertCircle,
  FileText,
  Loader2,
  Paperclip,
  ScanText,
  X,
} from 'lucide-react';
import { receiptApi } from '@/api/receipt.api';
import type { OcrStatus } from '@/types/budget.types';

interface Props {
  transactionId: string;
  hasReceipt: boolean;
  month: string;
  ocrStatus: OcrStatus | null;
  ocrText: string | null;
}

const ACCEPT = 'image/jpeg,image/png,image/webp,application/pdf';

export function ReceiptAction({
  transactionId,
  hasReceipt,
  month,
  ocrStatus,
  ocrText,
}: Props) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [busy, setBusy] = useState<null | 'upload' | 'remove' | 'view'>(null);
  const [textOpen, setTextOpen] = useState(false);

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
      const signed = await receiptApi.signedUrl(transactionId);
      window.open(signed.url, '_blank', 'noopener,noreferrer');
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
    <div className="relative flex items-center gap-0.5">
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
      <OcrIndicator
        status={ocrStatus}
        text={ocrText}
        open={textOpen}
        onToggle={() => setTextOpen((v) => !v)}
        labels={{
          processing: t('budget.receipt.ocrProcessing'),
          success: t('budget.receipt.ocrView'),
          failed: t('budget.receipt.ocrFailed'),
          retry: t('budget.receipt.ocrRetry'),
          empty: t('budget.receipt.ocrEmpty'),
        }}
      />
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

interface OcrIndicatorProps {
  status: OcrStatus | null;
  text: string | null;
  open: boolean;
  onToggle: () => void;
  labels: {
    processing: string;
    success: string;
    failed: string;
    retry: string;
    empty: string;
  };
}

function OcrIndicator({ status, text, open, onToggle, labels }: OcrIndicatorProps) {
  if (status === null) return null;

  if (status === 'PENDING' || status === 'IN_PROGRESS') {
    return (
      <span
        title={labels.processing}
        className="inline-flex items-center justify-center p-1"
      >
        <Loader2 className="w-3.5 h-3.5 animate-spin text-muted-foreground" />
      </span>
    );
  }

  if (status === 'FAILED' || status === 'RETRY') {
    return (
      <span
        title={status === 'RETRY' ? labels.retry : labels.failed}
        className="inline-flex items-center justify-center p-1"
      >
        <AlertCircle className="w-3.5 h-3.5 text-amber-400/70" />
      </span>
    );
  }

  // SUCCESS
  return (
    <>
      <button
        type="button"
        onClick={onToggle}
        title={labels.success}
        className="p-1 rounded hover:bg-emerald-500/10 cursor-pointer"
      >
        <ScanText className="w-3.5 h-3.5 text-emerald-400" />
      </button>
      {open && (
        <div
          role="dialog"
          className="absolute z-30 top-full right-0 mt-1 w-72 rounded-md border border-border bg-popover shadow-lg p-2 text-xs text-popover-foreground"
        >
          {text && text.length > 0 ? (
            <pre className="whitespace-pre-wrap break-words font-mono leading-snug max-h-48 overflow-auto">
              {text}
            </pre>
          ) : (
            <span className="text-muted-foreground">{labels.empty}</span>
          )}
        </div>
      )}
    </>
  );
}
