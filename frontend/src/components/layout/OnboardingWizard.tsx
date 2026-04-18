import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import {
  Sparkles,
  Palette,
  Compass,
  TrendingUp,
  ChevronRight,
  ChevronLeft,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { settingsApi } from '@/api/settings.api';
import { useCompleteOnboarding, useUpdateSettings } from '@/hooks/useSettings';

type Step = 0 | 1 | 2;

const CURRENCIES = [
  { code: 'TRY', label: 'Turkish Lira (₺)' },
  { code: 'USD', label: 'US Dollar ($)' },
  { code: 'EUR', label: 'Euro (€)' },
  { code: 'GBP', label: 'British Pound (£)' },
];

const LANGUAGES = [
  { code: 'tr', label: 'Türkçe' },
  { code: 'en', label: 'English' },
];

const THEMES = [
  { code: 'dark', label: 'Dark' },
  { code: 'light', label: 'Light' },
  { code: 'system', label: 'System' },
];

export function OnboardingWizard() {
  const { t } = useTranslation();

  const settings = useQuery({
    queryKey: ['settings'],
    queryFn: settingsApi.get,
    staleTime: 60_000,
  });

  const updateSettings = useUpdateSettings();
  const completeOnboarding = useCompleteOnboarding();

  const [step, setStep] = useState<Step>(0);
  const [currency, setCurrency] = useState<string | null>(null);
  const [language, setLanguage] = useState<string | null>(null);
  const [theme, setTheme] = useState<string | null>(null);

  const open = !!settings.data && settings.data.onboardingCompleted === false;

  const effectiveCurrency = currency ?? settings.data?.currency ?? 'TRY';
  const effectiveLanguage = language ?? settings.data?.language ?? 'tr';
  const effectiveTheme = theme ?? settings.data?.theme ?? 'dark';

  const finishing = updateSettings.isPending || completeOnboarding.isPending;

  const finish = async () => {
    const patch: Record<string, string> = {};
    if (currency && currency !== settings.data?.currency) patch.currency = currency;
    if (language && language !== settings.data?.language) patch.language = language;
    if (theme && theme !== settings.data?.theme) patch.theme = theme;
    if (Object.keys(patch).length > 0) {
      await updateSettings.mutateAsync(patch);
    }
    await completeOnboarding.mutateAsync();
  };

  const stepMeta = useMemo(
    () => [
      { icon: Sparkles, title: t('onboarding.welcomeTitle'), body: t('onboarding.welcomeBody') },
      { icon: Palette, title: t('onboarding.prefsTitle'), body: t('onboarding.prefsBody') },
      { icon: Compass, title: t('onboarding.tourTitle'), body: t('onboarding.tourBody') },
    ],
    [t],
  );

  const current = stepMeta[step];
  const Icon = current.icon;

  return (
    <Dialog open={open}>
      <DialogContent
        className="sm:max-w-[520px] p-0 overflow-hidden gap-0"
        hideClose
        onEscapeKeyDown={(e) => e.preventDefault()}
        onPointerDownOutside={(e) => e.preventDefault()}
      >
        {/* Gradient header */}
        <div className="relative bg-gradient-to-br from-primary/15 via-primary/5 to-background border-b border-border px-6 pt-6 pb-5">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-primary/15 text-primary flex items-center justify-center border border-primary/20">
              <Icon className="w-5 h-5" />
            </div>
            <div className="flex-1 min-w-0">
              <DialogHeader className="space-y-0">
                <DialogTitle className="text-base font-semibold tracking-tight">
                  {current.title}
                </DialogTitle>
              </DialogHeader>
              <p className="text-xs text-muted-foreground mt-1">
                {t('app.tagline')}
              </p>
            </div>
          </div>

          {/* Step dots */}
          <div className="flex items-center gap-1.5 mt-5">
            {[0, 1, 2].map((i) => (
              <span
                key={i}
                className={cn(
                  'h-1 rounded-full transition-all',
                  i === step ? 'w-8 bg-primary' : 'w-4 bg-muted',
                )}
              />
            ))}
          </div>
        </div>

        {/* Body */}
        <div className="px-6 py-5 space-y-5 min-h-[220px]">
          <p className="text-sm text-muted-foreground leading-relaxed">{current.body}</p>

          {step === 0 && (
            <div className="rounded-lg border border-border bg-muted/30 p-4 flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
                <TrendingUp className="w-4 h-4 text-primary-foreground" />
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium">{t('app.name')}</p>
                <p className="text-[11px] text-muted-foreground">{t('app.tagline')}</p>
              </div>
            </div>
          )}

          {step === 1 && (
            <div className="space-y-4">
              <PickerRow
                label={t('settings.currency')}
                value={effectiveCurrency}
                onChange={setCurrency}
                options={CURRENCIES}
              />
              <PickerRow
                label={t('settings.language')}
                value={effectiveLanguage}
                onChange={setLanguage}
                options={LANGUAGES}
              />
              <PickerRow
                label={t('settings.theme')}
                value={effectiveTheme}
                onChange={setTheme}
                options={THEMES}
              />
            </div>
          )}

          {step === 2 && (
            <ul className="space-y-2 text-sm">
              <TourItem label={t('nav.dashboard')} hint={t('common.thisMonth')} />
              <TourItem label={t('nav.portfolio')} hint={t('nav.prices')} />
              <TourItem label={t('nav.budget')} hint={t('budget.recentTransactions')} />
              <TourItem label={t('nav.bills')} hint={t('bills.dueThisMonth')} />
              <TourItem label={t('nav.analytics')} hint={t('nav.analytics')} />
            </ul>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-border bg-muted/20 flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={() => completeOnboarding.mutate()}
            disabled={finishing}
            className="text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer disabled:opacity-50"
          >
            {t('onboarding.skip')}
          </button>
          <div className="flex items-center gap-2">
            {step > 0 && (
              <Button
                variant="outline"
                size="sm"
                disabled={finishing}
                onClick={() => setStep((s) => (s > 0 ? ((s - 1) as Step) : s))}
                className="cursor-pointer"
              >
                <ChevronLeft className="w-3.5 h-3.5 mr-1" />
                {t('onboarding.back')}
              </Button>
            )}
            {step < 2 ? (
              <Button
                size="sm"
                onClick={() => setStep((s) => ((s + 1) as Step))}
                className="cursor-pointer"
              >
                {t('onboarding.next')}
                <ChevronRight className="w-3.5 h-3.5 ml-1" />
              </Button>
            ) : (
              <Button
                size="sm"
                onClick={finish}
                disabled={finishing}
                className="cursor-pointer"
              >
                {finishing ? t('common.saving') : t('onboarding.finish')}
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function PickerRow({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { code: string; label: string }[];
}) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs">{label}</Label>
      <div className="flex flex-wrap gap-1.5">
        {options.map((o) => {
          const active = o.code === value;
          return (
            <button
              key={o.code}
              type="button"
              onClick={() => onChange(o.code)}
              className={cn(
                'text-xs h-8 px-3 rounded-md border transition-colors cursor-pointer',
                active
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border text-muted-foreground hover:text-foreground hover:border-primary/40',
              )}
            >
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function TourItem({ label, hint }: { label: string; hint: string }) {
  return (
    <li className="flex items-center gap-3 rounded-md border border-border px-3 py-2">
      <span className="w-1.5 h-1.5 rounded-full bg-primary" />
      <span className="flex-1 text-sm font-medium">{label}</span>
      <span className="text-[11px] text-muted-foreground">{hint}</span>
    </li>
  );
}
