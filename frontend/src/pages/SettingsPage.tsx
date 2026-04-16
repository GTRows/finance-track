import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PageHeader } from '@/components/layout/PageHeader';
import { useAuthStore } from '@/store/auth.store';
import { useThemeStore, type Theme } from '@/store/theme.store';
import { useSettingsStore } from '@/store/settings.store';
import { useUpdateSettings } from '@/hooks/useSettings';
import { User, Bell, Palette, Globe, Shield, Check, Sun, Moon, Monitor, FileSpreadsheet } from 'lucide-react';
import { ImportExcelSection } from '@/components/settings/ImportExcelSection';
import { TotpSection } from '@/components/settings/TotpSection';
import { cn } from '@/lib/utils';

const CURRENCY_OPTIONS = ['TRY', 'USD', 'EUR', 'GBP'];

interface SettingsSectionProps {
  icon: React.ElementType;
  title: string;
  description: string;
  children: React.ReactNode;
}

function SettingsSection({ icon: Icon, title, description, children }: SettingsSectionProps) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-start gap-3">
          <div className="w-9 h-9 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Icon className="w-4 h-4 text-primary" />
          </div>
          <div>
            <CardTitle className="text-base">{title}</CardTitle>
            <CardDescription className="mt-0.5">{description}</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

const THEME_OPTIONS: Array<{ code: Theme; labelKey: string; icon: typeof Sun }> = [
  { code: 'light', labelKey: 'settings.themeLight', icon: Sun },
  { code: 'dark', labelKey: 'settings.themeDark', icon: Moon },
  { code: 'system', labelKey: 'settings.themeSystem', icon: Monitor },
];

export function SettingsPage() {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const theme = useThemeStore((s) => s.theme);
  const setTheme = useThemeStore((s) => s.setTheme);
  const settings = useSettingsStore((s) => s.settings);
  const updateSettings = useUpdateSettings();

  const handleLanguageChange = (code: 'tr' | 'en') => {
    i18n.changeLanguage(code);
    updateSettings.mutate({ language: code });
  };

  const handleThemeChange = (code: Theme) => {
    setTheme(code);
    updateSettings.mutate({ theme: code });
  };

  const handleCurrencyChange = (code: string) => {
    updateSettings.mutate({ currency: code });
  };

  const languages: Array<{ code: 'tr' | 'en'; label: string; flag: string }> = [
    { code: 'tr', label: t('settings.languageTurkish'), flag: 'TR' },
    { code: 'en', label: t('settings.languageEnglish'), flag: 'EN' },
  ];

  return (
    <div className="space-y-6 max-w-[800px]">
      <PageHeader title={t('settings.title')} description={t('settings.description')} />

      <SettingsSection
        icon={User}
        title={t('settings.profile')}
        description={t('settings.profileDesc')}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="username">{t('settings.username')}</Label>
            <Input id="username" value={user?.username ?? ''} disabled />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="email">{t('settings.email')}</Label>
            <Input id="email" value={user?.email ?? ''} disabled />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="role">{t('settings.role')}</Label>
            <Input id="role" value={user?.role ?? ''} disabled />
          </div>
        </div>
      </SettingsSection>

      <SettingsSection
        icon={Globe}
        title={t('settings.localization')}
        description={t('settings.localizationDesc')}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label>{t('settings.currency')}</Label>
            <div className="flex flex-wrap gap-2">
              {CURRENCY_OPTIONS.map((code) => {
                const active = settings.currency === code;
                return (
                  <button
                    key={code}
                    type="button"
                    onClick={() => handleCurrencyChange(code)}
                    className={cn(
                      'flex items-center gap-2 h-9 px-3 rounded-md border text-sm font-medium transition-colors cursor-pointer',
                      active
                        ? 'border-primary bg-primary/10 text-primary'
                        : 'border-input text-muted-foreground hover:text-foreground hover:bg-accent'
                    )}
                  >
                    <span>{code}</span>
                    {active && <Check className="w-3.5 h-3.5" strokeWidth={3} />}
                  </button>
                );
              })}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label>{t('settings.language')}</Label>
            <div className="flex gap-2">
              {languages.map((lang) => {
                const active = i18n.resolvedLanguage === lang.code;
                return (
                  <button
                    key={lang.code}
                    type="button"
                    onClick={() => handleLanguageChange(lang.code)}
                    className={cn(
                      'flex items-center gap-2 h-9 px-3 rounded-md border text-sm font-medium transition-colors cursor-pointer',
                      active
                        ? 'border-primary bg-primary/10 text-primary'
                        : 'border-input text-muted-foreground hover:text-foreground hover:bg-accent'
                    )}
                  >
                    <span className="text-[10px] font-semibold tracking-wider">{lang.flag}</span>
                    <span>{lang.label}</span>
                    {active && <Check className="w-3.5 h-3.5" strokeWidth={3} />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </SettingsSection>

      <SettingsSection
        icon={Palette}
        title={t('settings.appearance')}
        description={t('settings.appearanceDesc')}
      >
        <div className="flex flex-wrap gap-2">
          {THEME_OPTIONS.map((opt) => {
            const active = opt.code === theme;
            const Icon = opt.icon;
            return (
              <button
                key={opt.code}
                type="button"
                onClick={() => handleThemeChange(opt.code)}
                className={cn(
                  'flex items-center gap-2 h-9 px-3 rounded-md border text-sm font-medium transition-colors cursor-pointer',
                  active
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-input text-muted-foreground hover:text-foreground hover:bg-accent'
                )}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{t(opt.labelKey)}</span>
                {active && <Check className="w-3.5 h-3.5" strokeWidth={3} />}
              </button>
            );
          })}
        </div>
      </SettingsSection>

      <SettingsSection
        icon={Bell}
        title={t('settings.notifications')}
        description={t('settings.notificationsDesc')}
      >
        <p className="text-sm text-muted-foreground">{t('settings.notificationsSoon')}</p>
      </SettingsSection>

      <SettingsSection
        icon={FileSpreadsheet}
        title={t('settings.importTitle')}
        description={t('settings.importDescription')}
      >
        <ImportExcelSection />
      </SettingsSection>

      <SettingsSection
        icon={Shield}
        title={t('settings.security')}
        description={t('settings.securityDesc')}
      >
        <div className="space-y-5">
          <TotpSection />
          <div className="border-t pt-4 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">{t('settings.password')}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{t('settings.passwordLastChanged')}</p>
            </div>
            <Button variant="outline" size="sm" className="cursor-pointer" disabled>
              {t('settings.changePassword')}
            </Button>
          </div>
        </div>
      </SettingsSection>
    </div>
  );
}
