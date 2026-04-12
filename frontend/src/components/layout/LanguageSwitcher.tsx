import { useTranslation } from 'react-i18next';
import { Globe, Check } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';

interface LanguageOption {
  code: 'tr' | 'en';
  label: string;
  flag: string;
}

const LANGUAGES: LanguageOption[] = [
  { code: 'tr', label: 'Türkçe', flag: 'TR' },
  { code: 'en', label: 'English', flag: 'EN' },
];

export function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const current = LANGUAGES.find((l) => l.code === i18n.resolvedLanguage) ?? LANGUAGES[0];

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

  const handleSelect = (code: LanguageOption['code']) => {
    i18n.changeLanguage(code);
    setOpen(false);
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        title={current.label}
        className="flex items-center gap-1.5 h-8 px-2.5 rounded-md text-xs font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer"
      >
        <Globe className="w-3.5 h-3.5" />
        <span className="tabular-nums">{current.flag}</span>
      </button>
      {open && (
        <div className="absolute right-0 top-10 z-30 min-w-[160px] rounded-md border bg-popover shadow-lg p-1">
          {LANGUAGES.map((lang) => {
            const active = lang.code === current.code;
            return (
              <button
                key={lang.code}
                type="button"
                onClick={() => handleSelect(lang.code)}
                className={cn(
                  'w-full flex items-center justify-between gap-2 px-2.5 py-2 text-sm rounded cursor-pointer transition-colors',
                  active ? 'bg-accent text-foreground' : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground'
                )}
              >
                <span className="flex items-center gap-2">
                  <span className="text-[10px] font-semibold tracking-wider text-muted-foreground">
                    {lang.flag}
                  </span>
                  <span>{lang.label}</span>
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
