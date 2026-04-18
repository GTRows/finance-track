import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import {
  Search,
  LayoutDashboard,
  Briefcase,
  Wallet,
  Receipt,
  BarChart3,
  LineChart,
  Bell,
  Settings,
  CornerDownLeft,
  ArrowUp,
  ArrowDown,
  Clock,
} from 'lucide-react';
import { usePortfolios } from '@/hooks/usePortfolios';
import { useBills } from '@/hooks/useBills';
import { cn } from '@/lib/utils';

type ItemKind = 'nav' | 'portfolio' | 'bill' | 'action';

interface PaletteItem {
  id: string;
  kind: ItemKind;
  label: string;
  group: string;
  icon: React.ComponentType<{ className?: string }>;
  to: string;
  keywords?: string;
  meta?: string;
}

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const RECENT_KEY = 'fintrack.palette.recent';
const RECENT_MAX = 5;

function readRecent(): string[] {
  try {
    const raw = localStorage.getItem(RECENT_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string').slice(0, RECENT_MAX) : [];
  } catch {
    return [];
  }
}

function pushRecent(id: string): void {
  const current = readRecent().filter((x) => x !== id);
  current.unshift(id);
  localStorage.setItem(RECENT_KEY, JSON.stringify(current.slice(0, RECENT_MAX)));
}

export function CommandPalette({ open, onOpenChange }: CommandPaletteProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const listRef = useRef<HTMLDivElement | null>(null);

  const { data: portfolios } = usePortfolios();
  const { data: bills } = useBills();

  const items = useMemo<PaletteItem[]>(() => {
    const navItems: PaletteItem[] = [
      { id: 'nav:dashboard', kind: 'nav', label: t('nav.dashboard'), group: t('palette.groupNavigate'), icon: LayoutDashboard, to: '/' },
      { id: 'nav:portfolio', kind: 'nav', label: t('nav.portfolio'), group: t('palette.groupNavigate'), icon: Briefcase, to: '/portfolio' },
      { id: 'nav:budget', kind: 'nav', label: t('nav.budget'), group: t('palette.groupNavigate'), icon: Wallet, to: '/budget' },
      { id: 'nav:bills', kind: 'nav', label: t('nav.bills'), group: t('palette.groupNavigate'), icon: Receipt, to: '/bills' },
      { id: 'nav:analytics', kind: 'nav', label: t('nav.analytics'), group: t('palette.groupNavigate'), icon: BarChart3, to: '/analytics' },
      { id: 'nav:prices', kind: 'nav', label: t('nav.prices'), group: t('palette.groupNavigate'), icon: LineChart, to: '/prices' },
      { id: 'nav:alerts', kind: 'nav', label: t('nav.alerts'), group: t('palette.groupNavigate'), icon: Bell, to: '/alerts' },
      { id: 'nav:settings', kind: 'nav', label: t('nav.settings'), group: t('palette.groupNavigate'), icon: Settings, to: '/settings' },
    ];

    const portfolioItems: PaletteItem[] = (portfolios ?? []).map((p) => ({
      id: `portfolio:${p.id}`,
      kind: 'portfolio',
      label: p.name,
      group: t('palette.groupPortfolios'),
      icon: Briefcase,
      to: `/portfolio/${p.id}`,
      meta: p.type,
      keywords: `${p.name} ${p.type}`,
    }));

    const billItems: PaletteItem[] = (bills ?? []).map((b) => ({
      id: `bill:${b.id}`,
      kind: 'bill',
      label: b.name,
      group: t('palette.groupBills'),
      icon: Receipt,
      to: '/bills',
      meta: b.category,
      keywords: `${b.name} ${b.category}`,
    }));

    return [...navItems, ...portfolioItems, ...billItems];
  }, [portfolios, bills, t]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      const recent = readRecent();
      if (recent.length > 0) {
        const byId = new Map(items.map((i) => [i.id, i]));
        const recentItems = recent
          .map((id) => byId.get(id))
          .filter((x): x is PaletteItem => !!x)
          .map((item) => ({ ...item, group: t('palette.groupRecent'), icon: Clock }));
        const recentIds = new Set(recent);
        const rest = items.filter((i) => !recentIds.has(i.id));
        return [...recentItems, ...rest];
      }
      return items;
    }
    return items.filter((item) => {
      const hay = `${item.label} ${item.keywords ?? ''}`.toLowerCase();
      return hay.includes(q);
    });
  }, [items, query, t]);

  const grouped = useMemo(() => {
    const groups: { name: string; items: PaletteItem[] }[] = [];
    for (const item of filtered) {
      const last = groups[groups.length - 1];
      if (last && last.name === item.group) {
        last.items.push(item);
      } else {
        groups.push({ name: item.group, items: [item] });
      }
    }
    return groups;
  }, [filtered]);

  useEffect(() => {
    setSelected(0);
  }, [query, filtered.length]);

  useEffect(() => {
    if (!open) {
      setQuery('');
      setSelected(0);
    }
  }, [open]);

  useEffect(() => {
    if (!listRef.current) return;
    const el = listRef.current.querySelector<HTMLElement>(`[data-index="${selected}"]`);
    if (el) {
      el.scrollIntoView({ block: 'nearest' });
    }
  }, [selected]);

  const runItem = (item: PaletteItem) => {
    pushRecent(item.id);
    onOpenChange(false);
    navigate(item.to);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelected((s) => Math.min(s + 1, filtered.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = filtered[selected];
      if (item) runItem(item);
    }
  };

  let runningIndex = -1;

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay
          className={cn(
            'fixed inset-0 z-50 bg-background/60 backdrop-blur-sm',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0'
          )}
        />
        <DialogPrimitive.Content
          onKeyDown={onKeyDown}
          className={cn(
            'fixed left-1/2 top-[15vh] z-50 w-full max-w-[640px] -translate-x-1/2 px-4',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0',
            'data-[state=open]:zoom-in-98 data-[state=closed]:zoom-out-98',
            'data-[state=open]:slide-in-from-top-4'
          )}
        >
          <DialogPrimitive.Title className="sr-only">{t('palette.title')}</DialogPrimitive.Title>
          <DialogPrimitive.Description className="sr-only">
            {t('palette.description')}
          </DialogPrimitive.Description>

          <div className="overflow-hidden rounded-xl border border-border/80 bg-card shadow-2xl shadow-black/40 ring-1 ring-black/5">
            {/* Search row */}
            <div className="flex items-center gap-3 border-b border-border/70 px-4 h-14">
              <Search className="w-4 h-4 text-muted-foreground flex-shrink-0" />
              <input
                autoFocus
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={t('palette.placeholder')}
                className="flex-1 bg-transparent text-[15px] tracking-tight outline-none placeholder:text-muted-foreground/70"
              />
              <kbd className="hidden sm:inline-flex items-center h-6 px-1.5 rounded border border-border bg-muted/40 text-[10px] font-mono uppercase tracking-wider text-muted-foreground">
                Esc
              </kbd>
            </div>

            {/* Results */}
            <div ref={listRef} className="max-h-[50vh] overflow-y-auto py-2">
              {grouped.length === 0 ? (
                <div className="px-4 py-10 text-center">
                  <div className="text-sm text-muted-foreground">
                    {t('palette.empty')}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground/70">
                    {t('palette.emptyHint')}
                  </div>
                </div>
              ) : (
                grouped.map((group) => (
                  <div key={group.name} className="px-2 pb-1">
                    <div className="px-3 pt-3 pb-1.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground/70">
                      {group.name}
                    </div>
                    {group.items.map((item) => {
                      runningIndex += 1;
                      const isSelected = runningIndex === selected;
                      const idx = runningIndex;
                      const Icon = item.icon;
                      return (
                        <button
                          key={item.id}
                          type="button"
                          data-index={idx}
                          onMouseEnter={() => setSelected(idx)}
                          onClick={() => runItem(item)}
                          className={cn(
                            'relative w-full flex items-center gap-3 rounded-md px-3 h-10 text-left transition-colors cursor-pointer',
                            isSelected
                              ? 'bg-primary/10 text-foreground'
                              : 'text-foreground/90 hover:bg-accent/40'
                          )}
                        >
                          <span
                            className={cn(
                              'absolute left-0 top-1.5 bottom-1.5 w-0.5 rounded-full transition-colors',
                              isSelected ? 'bg-primary' : 'bg-transparent'
                            )}
                          />
                          <Icon
                            className={cn(
                              'w-4 h-4 flex-shrink-0',
                              isSelected ? 'text-primary' : 'text-muted-foreground'
                            )}
                          />
                          <span className="text-sm tracking-tight truncate">{item.label}</span>
                          {item.meta ? (
                            <span className="ml-auto text-[11px] font-mono uppercase tracking-wider text-muted-foreground/80">
                              {item.meta}
                            </span>
                          ) : null}
                          {isSelected ? (
                            <CornerDownLeft
                              className={cn(
                                'w-3.5 h-3.5 text-muted-foreground/70',
                                item.meta ? 'ml-2' : 'ml-auto'
                              )}
                            />
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                ))
              )}
            </div>

            {/* Footer */}
            <div className="flex items-center gap-4 border-t border-border/70 bg-muted/20 px-4 h-9 text-[11px] text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <kbd className="inline-flex items-center justify-center w-5 h-5 rounded border border-border bg-background/60 font-mono">
                  <ArrowUp className="w-3 h-3" />
                </kbd>
                <kbd className="inline-flex items-center justify-center w-5 h-5 rounded border border-border bg-background/60 font-mono">
                  <ArrowDown className="w-3 h-3" />
                </kbd>
                {t('palette.hintNavigate')}
              </span>
              <span className="flex items-center gap-1.5">
                <kbd className="inline-flex items-center justify-center h-5 px-1 rounded border border-border bg-background/60 font-mono">
                  <CornerDownLeft className="w-3 h-3" />
                </kbd>
                {t('palette.hintSelect')}
              </span>
              <span className="ml-auto hidden sm:flex items-center gap-1.5">
                <kbd className="inline-flex items-center h-5 px-1.5 rounded border border-border bg-background/60 font-mono text-[10px] uppercase tracking-wider">
                  {navigator.platform.toLowerCase().includes('mac') ? 'Cmd' : 'Ctrl'}
                </kbd>
                +
                <kbd className="inline-flex items-center h-5 px-1.5 rounded border border-border bg-background/60 font-mono text-[10px] uppercase tracking-wider">
                  K
                </kbd>
              </span>
            </div>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
