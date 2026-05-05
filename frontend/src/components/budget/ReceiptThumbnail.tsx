import { AlertCircle, ImageOff, Loader2 } from 'lucide-react';
import { useReceiptUrl } from '@/hooks/useReceiptUrl';

interface Props {
  transactionId: string;
  alt: string;
  className?: string;
}

export function ReceiptThumbnail({ transactionId, alt, className }: Props) {
  const { data, isLoading, isError } = useReceiptUrl(transactionId, true);

  if (isLoading) {
    return (
      <div
        className={
          'flex items-center justify-center rounded bg-muted/50 ' + (className ?? '')
        }
      >
        <Loader2 className="w-4 h-4 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (isError || !data?.url) {
    return (
      <div
        className={
          'flex items-center justify-center rounded bg-muted/50 text-muted-foreground ' +
          (className ?? '')
        }
        title="Receipt unavailable"
      >
        {isError ? <AlertCircle className="w-4 h-4" /> : <ImageOff className="w-4 h-4" />}
      </div>
    );
  }

  return <img src={data.url} alt={alt} className={className} loading="lazy" />;
}
