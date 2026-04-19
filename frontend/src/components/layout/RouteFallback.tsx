import { Loader2 } from 'lucide-react';

export function RouteFallback() {
  return (
    <div className="min-h-[60vh] w-full flex items-center justify-center">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="w-4 h-4 animate-spin" />
        <span>Loading...</span>
      </div>
    </div>
  );
}
