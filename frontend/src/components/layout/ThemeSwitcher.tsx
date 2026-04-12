import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Sun, Moon, Monitor, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useThemeStore, type Theme } from '@/store/theme.store';

interface Option {
  code: Theme;
  labelKey: string;
  icon: typeof Sun;
}

const OPTIONS: Option[] = [
  { code: 'light', labelKey: 'settings.themeLight', icon: Sun },
  { code: 'dark', labelKey: 'settings.themeDark', icon: Moon },
  { code: 'system', labelKey: 'settings.themeSystem', icon: Monitor },
];

export function ThemeSwitcher() {
  const { t } = useTranslation();
  const theme = useThemeStore((s) => s.theme);
  const setTheme = useThemeStore((s) => s.setTheme);
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const current = OPTIONS.find((o) => o.code === theme) ?? OPTIONS[1];
  const CurrentIcon = current.icon;

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  const handleSelect = (code: Theme) => {
    setTheme(code);
    setOpen(false);
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        title={t(current.labelKey)}
        className="flex items-center justify-center h-8 w-8 rounded-md text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer"
      >
        <CurrentIcon className="w-4 h-4" />
      </button>
      {open && (
        <div className="absolute right-0 top-10 z-30 min-w-[160px] rounded-md border bg-popover shadow-lg p-1">
          {OPTIONS.map((opt) => {
            const active = opt.code === theme;
            const Icon = opt.icon;
            return (
              <button
                key={opt.code}
                type="button"
                onClick={() => handleSelect(opt.code)}
                className={cn(
                  'w-full flex items-center justify-between gap-2 px-2.5 py-2 text-sm rounded cursor-pointer transition-colors',
                  active ? 'bg-accent text-foreground' : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground'
                )}
              >
                <span className="flex items-center gap-2">
                  <Icon className="w-3.5 h-3.5" />
                  <span>{t(opt.labelKey)}</span>
                </span>
                {active && <Check className="w-3.5 h-3.5" strokeWidth={3} />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
