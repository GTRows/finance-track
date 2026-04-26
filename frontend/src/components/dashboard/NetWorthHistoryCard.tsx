import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceDot,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  useNetWorth,
  useCreateNetWorthEvent,
  useUpdateNetWorthEvent,
  useDeleteNetWorthEvent,
} from '@/hooks/useNetWorth';
import type {
  NetWorthEvent,
  NetWorthEventType,
  UpsertEventRequest,
} from '@/types/networth.types';
import { formatTRY, formatShortDate } from '@/utils/formatters';
import { cn } from '@/lib/utils';
import {
  Plus,
  ShoppingBag,
  Banknote,
  Flag,
  StickyNote,
  Pencil,
  Trash2,
  Landmark,
} from 'lucide-react';

const TYPE_META: Record<
  NetWorthEventType,
  { icon: typeof ShoppingBag; color: string; bg: string; ring: string }
> = {
  PURCHASE: {
    icon: ShoppingBag,
    color: 'text-rose-400',
    bg: 'bg-rose-500/10',
    ring: 'ring-rose-400/40',
  },
  INCOME: {
    icon: Banknote,
    color: 'text-emerald-400',
    bg: 'bg-emerald-500/10',
    ring: 'ring-emerald-400/40',
  },
  MILESTONE: {
    icon: Flag,
    color: 'text-amber-400',
    bg: 'bg-amber-500/10',
    ring: 'ring-amber-400/40',
  },
  NOTE: {
    icon: StickyNote,
    color: 'text-sky-400',
    bg: 'bg-sky-500/10',
    ring: 'ring-sky-400/40',
  },
};

function compact(value: number): string {
  if (value === 0) return '0';
  const abs = Math.abs(value);
  if (abs >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`;
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (abs >= 1_000) return `${(value / 1_000).toFixed(0)}K`;
  return value.toFixed(0);
}

export function NetWorthHistoryCard() {
  const { t } = useTranslation();
  const { data, isLoading } = useNetWorth();
  const [editing, setEditing] = useState<NetWorthEvent | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  const series = useMemo(() => data?.series ?? [], [data?.series]);
  const events = useMemo(() => data?.events ?? [], [data?.events]);

  const chartData = useMemo(() => {
    return series.map((p) => ({
      ...p,
      timestamp: new Date(p.date).getTime(),
    }));
  }, [series]);

  const seriesDateSet = useMemo(
    () => new Set(chartData.map((p) => p.date)),
    [chartData]
  );

  const eventMarkers = useMemo(() => {
    return events
      .filter((e) => seriesDateSet.has(e.eventDate))
      .map((e) => {
        const point = chartData.find((p) => p.date === e.eventDate);
        return {
          event: e,
          x: point?.timestamp ?? null,
          y: point?.totalValueTry ?? null,
        };
      })
      .filter((m): m is { event: NetWorthEvent; x: number; y: number } => m.x !== null);
  }, [events, chartData, seriesDateSet]);

  const latestValue = series.length > 0 ? series[series.length - 1].totalValueTry : 0;
  const firstValue = series.length > 0 ? series[0].totalValueTry : 0;
  const change = latestValue - firstValue;
  const changePct = firstValue > 0 ? change / firstValue : 0;

  return (
    <Card className="overflow-hidden">
      <div className="px-5 py-4 border-b border-border/60 flex items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
            <Landmark className="w-4 h-4 text-primary" />
          </div>
          <div className="space-y-0.5">
            <h3 className="text-sm font-medium">{t('networth.title')}</h3>
            <p className="text-[11px] text-muted-foreground">{t('networth.subtitle')}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          {series.length > 1 && (
            <div className="text-right">
              <p className="text-lg font-semibold font-mono tabular-nums tracking-tight">
                {formatTRY(latestValue)}
              </p>
              <p
                className={cn(
                  'text-[11px] font-mono tabular-nums',
                  change >= 0 ? 'text-emerald-400' : 'text-rose-400'
                )}
              >
                {change >= 0 ? '+' : ''}
                {formatTRY(change)} ({(changePct * 100).toFixed(1)}%)
              </p>
            </div>
          )}
          <EventDialog
            open={dialogOpen && !editing}
            onOpenChange={(v) => {
              setDialogOpen(v);
              if (!v) setEditing(null);
            }}
            trigger={
              <Button size="sm" variant="outline" className="h-8 cursor-pointer">
                <Plus className="w-3.5 h-3.5 mr-1" />
                {t('networth.addEvent')}
              </Button>
            }
          />
        </div>
      </div>

      <CardContent className="p-5 space-y-4">
        {isLoading && series.length === 0 ? (
          <div className="h-64 flex items-center justify-center text-xs text-muted-foreground">
            {t('common.loading')}
          </div>
        ) : series.length < 2 ? (
          <div className="h-48 flex flex-col items-center justify-center gap-1 text-xs text-muted-foreground">
            <p>{t('networth.needSnapshots')}</p>
          </div>
        ) : (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="nwGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="timestamp"
                  type="number"
                  scale="time"
                  domain={['dataMin', 'dataMax']}
                  tickFormatter={(v: number) => formatShortDate(new Date(v).toISOString())}
                  tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                  stroke="hsl(var(--border))"
                />
                <YAxis
                  tickFormatter={(v: number) => compact(v)}
                  tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                  stroke="hsl(var(--border))"
                  width={48}
                />
                <Tooltip
                  cursor={{ stroke: 'hsl(var(--primary))', strokeWidth: 1, strokeDasharray: '3 3' }}
                  content={<NetWorthTooltip events={events} />}
                />
                <Area
                  type="monotone"
                  dataKey="totalValueTry"
                  stroke="hsl(var(--primary))"
                  strokeWidth={2}
                  fill="url(#nwGradient)"
                />
                {eventMarkers.map(({ event, x, y }) => {
                  const meta = TYPE_META[event.eventType];
                  const color =
                    event.eventType === 'PURCHASE'
                      ? '#fb7185'
                      : event.eventType === 'INCOME'
                        ? '#34d399'
                        : event.eventType === 'MILESTONE'
                          ? '#fbbf24'
                          : '#38bdf8';
                  void meta;
                  return (
                    <ReferenceDot
                      key={event.id}
                      x={x}
                      y={y}
                      r={5}
                      fill={color}
                      stroke="hsl(var(--background))"
                      strokeWidth={2}
                      isFront
                    />
                  );
                })}
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {events.length > 0 && (
          <ul className="divide-y divide-border/60 -mx-5">
            {events.slice(0, 6).map((event) => {
              const meta = TYPE_META[event.eventType];
              const Icon = meta.icon;
              return (
                <li
                  key={event.id}
                  className="px-5 py-2.5 flex items-center gap-3 group hover:bg-accent/30 transition-colors"
                >
                  <div
                    className={cn(
                      'w-7 h-7 rounded-md flex items-center justify-center shrink-0 ring-1',
                      meta.bg,
                      meta.ring
                    )}
                  >
                    <Icon className={cn('w-3.5 h-3.5', meta.color)} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{event.label}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {formatShortDate(event.eventDate)}
                      {event.note && <span className="ml-1.5">-- {event.note}</span>}
                    </p>
                  </div>
                  {event.impactTry != null && (
                    <span
                      className={cn(
                        'text-xs font-mono tabular-nums shrink-0',
                        event.impactTry >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      )}
                    >
                      {event.impactTry >= 0 ? '+' : ''}
                      {formatTRY(event.impactTry)}
                    </span>
                  )}
                  <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 transition-opacity">
                    <button
                      className="p-1 rounded hover:bg-accent cursor-pointer"
                      onClick={() => {
                        setEditing(event);
                        setDialogOpen(true);
                      }}
                      title={t('common.edit')}
                    >
                      <Pencil className="w-3 h-3 text-muted-foreground" />
                    </button>
                    <DeleteEventButton eventId={event.id} />
                  </div>
                </li>
              );
            })}
          </ul>
        )}

        {editing && (
          <EventDialog
            event={editing}
            open={dialogOpen}
            onOpenChange={(v) => {
              setDialogOpen(v);
              if (!v) setEditing(null);
            }}
          />
        )}
      </CardContent>
    </Card>
  );
}

function DeleteEventButton({ eventId }: { eventId: string }) {
  const { t } = useTranslation();
  const del = useDeleteNetWorthEvent();
  const [confirming, setConfirming] = useState(false);

  const handle = () => {
    if (confirming) {
      del.mutate(eventId);
      setConfirming(false);
    } else {
      setConfirming(true);
      setTimeout(() => setConfirming(false), 3000);
    }
  };

  return (
    <button
      className={cn(
        'p-1 rounded cursor-pointer',
        confirming ? 'bg-destructive/20 text-destructive' : 'hover:bg-destructive/10 text-muted-foreground'
      )}
      onClick={handle}
      title={confirming ? t('common.confirmAgain') : t('common.delete')}
    >
      <Trash2 className="w-3 h-3" />
    </button>
  );
}

interface TooltipPayload {
  active?: boolean;
  payload?: Array<{ payload: { date: string; totalValueTry: number } }>;
  events: NetWorthEvent[];
}

function NetWorthTooltip({ active, payload, events }: TooltipPayload) {
  if (!active || !payload || payload.length === 0) return null;
  const row = payload[0].payload;
  const matching = events.filter((e) => e.eventDate === row.date);
  return (
    <div className="rounded-md border border-border bg-background/95 backdrop-blur-sm px-3 py-2 shadow-lg">
      <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
        {formatShortDate(row.date)}
      </p>
      <p className="text-sm font-mono tabular-nums font-semibold">{formatTRY(row.totalValueTry)}</p>
      {matching.map((e) => {
        const meta = TYPE_META[e.eventType];
        const Icon = meta.icon;
        return (
          <div key={e.id} className="mt-1 flex items-center gap-1.5 text-[11px]">
            <Icon className={cn('w-3 h-3', meta.color)} />
            <span>{e.label}</span>
          </div>
        );
      })}
    </div>
  );
}

interface EventDialogProps {
  event?: NetWorthEvent;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  trigger?: React.ReactNode;
}

function EventDialog({ event, open, onOpenChange, trigger }: EventDialogProps) {
  const { t } = useTranslation();
  const create = useCreateNetWorthEvent();
  const update = useUpdateNetWorthEvent();
  const isEdit = !!event;

  const [date, setDate] = useState(event?.eventDate ?? new Date().toISOString().slice(0, 10));
  const [type, setType] = useState<NetWorthEventType>(event?.eventType ?? 'MILESTONE');
  const [label, setLabel] = useState(event?.label ?? '');
  const [note, setNote] = useState(event?.note ?? '');
  const [impact, setImpact] = useState(event?.impactTry != null ? String(event.impactTry) : '');

  const handleSubmit = async () => {
    if (!label.trim()) return;
    const req: UpsertEventRequest = {
      eventDate: date,
      eventType: type,
      label: label.trim(),
      note: note.trim() || undefined,
      impactTry: impact ? Number(impact.replace(',', '.')) : undefined,
    };

    try {
      if (isEdit && event) {
        await update.mutateAsync({ id: event.id, req });
      } else {
        await create.mutateAsync(req);
      }
      onOpenChange(false);
    } catch {
      // silent
    }
  };

  const pending = create.isPending || update.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {trigger && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent className="sm:max-w-[460px]">
        <DialogHeader>
          <DialogTitle>{isEdit ? t('networth.editEvent') : t('networth.addEvent')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-4 pt-1">
          <div className="space-y-1.5">
            <Label>{t('networth.label')}</Label>
            <Input
              value={label}
              placeholder={t('networth.labelPlaceholder')}
              onChange={(e) => setLabel(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>{t('networth.eventDate')}</Label>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('networth.impact')}</Label>
              <div className="relative">
                <Input
                  type="number"
                  step="0.01"
                  value={impact}
                  onChange={(e) => setImpact(e.target.value)}
                  placeholder="0"
                  className="pr-10 font-mono tabular-nums"
                />
                <span className="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] text-muted-foreground">
                  TRY
                </span>
              </div>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('networth.eventType')}</Label>
            <div className="grid grid-cols-4 gap-1.5">
              {(Object.keys(TYPE_META) as NetWorthEventType[]).map((k) => {
                const meta = TYPE_META[k];
                const Icon = meta.icon;
                const active = type === k;
                return (
                  <button
                    key={k}
                    type="button"
                    onClick={() => setType(k)}
                    className={cn(
                      'flex flex-col items-center gap-1 rounded-lg border py-2 cursor-pointer transition-colors',
                      active
                        ? 'border-primary bg-primary/5'
                        : 'border-border/60 hover:border-border'
                    )}
                  >
                    <Icon className={cn('w-4 h-4', meta.color)} />
                    <span className="text-[10px] uppercase tracking-wider">
                      {t(`networth.type.${k}`)}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>{t('networth.note')}</Label>
            <Textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder={t('networth.notePlaceholder')}
              rows={3}
            />
          </div>

          <Button
            onClick={handleSubmit}
            disabled={pending || !label.trim()}
            className="w-full cursor-pointer"
          >
            {pending ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
