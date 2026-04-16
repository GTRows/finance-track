import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { LanguageSwitcher } from '@/components/layout/LanguageSwitcher';
import { useAuthStore } from '@/store/auth.store';
import { authApi } from '@/api/auth.api';
import type { ApiError } from '@/types/auth.types';
import { AxiosError } from 'axios';
import {
  TrendingUp,
  Shield,
  BarChart3,
  ArrowRight,
  Eye,
  EyeOff,
  Loader2,
} from 'lucide-react';

function getPasswordScore(pw: string): number {
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++;
  if (/\d/.test(pw)) score++;
  if (/[^a-zA-Z0-9]/.test(pw)) score++;
  return score;
}

function getStrengthColor(score: number): string {
  if (score <= 1) return 'bg-red-500';
  if (score <= 2) return 'bg-orange-500';
  if (score <= 3) return 'bg-yellow-500';
  return 'bg-primary';
}

export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [isRegister, setIsRegister] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [challengeToken, setChallengeToken] = useState<string | null>(null);
  const [totpCode, setTotpCode] = useState('');

  const strengthScore = useMemo(() => getPasswordScore(password), [password]);
  const strengthLabels: Record<number, string> = {
    0: t('auth.strengthWeak'),
    1: t('auth.strengthWeak'),
    2: t('auth.strengthFair'),
    3: t('auth.strengthGood'),
    4: t('auth.strengthStrong'),
    5: t('auth.strengthStrong'),
  };
  const strength = {
    score: strengthScore,
    label: strengthLabels[strengthScore] ?? '',
    color: getStrengthColor(strengthScore),
  };
  const passwordsMatch = password === confirmPassword;
  const usernameValid = username.length >= 3;

  const registerFormValid =
    usernameValid && email.length > 0 && password.length >= 8 && passwordsMatch && confirmPassword.length > 0;
  const loginFormValid = username.length > 0 && password.length > 0;
  const formValid = isRegister ? registerFormValid : loginFormValid;

  const markTouched = (field: string) =>
    setTouched((prev) => ({ ...prev, [field]: true }));

  const resetForm = () => {
    setUsername('');
    setEmail('');
    setPassword('');
    setConfirmPassword('');
    setError(null);
    setTouched({});
    setShowPassword(false);
    setShowConfirm(false);
    setChallengeToken(null);
    setTotpCode('');
  };

  const completeAuth = (response: Awaited<ReturnType<typeof authApi.login>>) => {
    if (response.requiresTotp && response.totpChallengeToken) {
      setChallengeToken(response.totpChallengeToken);
      return;
    }
    if (response.user && response.accessToken && response.refreshToken) {
      setAuth(response.user, response.accessToken, response.refreshToken);
      navigate('/', { replace: true });
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (isRegister && !passwordsMatch) {
      setError(t('auth.passwordsNoMatch'));
      return;
    }
    if (isRegister && !usernameValid) {
      setError(t('auth.usernameMinError'));
      return;
    }

    setLoading(true);
    try {
      const response = isRegister
        ? await authApi.register({ username, email, password })
        : await authApi.login({ username, password });
      completeAuth(response);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      const message = axiosError.response?.data?.error || t('common.somethingWentWrong');
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleTotpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!challengeToken || totpCode.length !== 6) return;
    setLoading(true);
    try {
      const response = await authApi.verifyTotp({ challengeToken, code: totpCode });
      completeAuth(response);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      const message = axiosError.response?.data?.error || t('common.somethingWentWrong');
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const features = [
    { icon: TrendingUp, title: t('auth.featurePortfolioTitle'), desc: t('auth.featurePortfolioDesc') },
    { icon: BarChart3, title: t('auth.featureBudgetTitle'), desc: t('auth.featureBudgetDesc') },
    { icon: Shield, title: t('auth.featureSecureTitle'), desc: t('auth.featureSecureDesc') },
  ];

  return (
    <div className="min-h-screen flex">
      {/* Left brand panel */}
      <div className="hidden lg:flex lg:w-[480px] xl:w-[560px] flex-col justify-between bg-card p-10 relative overflow-hidden">
        {/* Background grid pattern */}
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage:
              'linear-gradient(hsl(var(--foreground)) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--foreground)) 1px, transparent 1px)',
            backgroundSize: '40px 40px',
          }}
        />

        {/* Gradient orb */}
        <div className="absolute -bottom-32 -left-32 w-96 h-96 rounded-full bg-primary/10 blur-[100px]" />

        <div className="relative z-10">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-9 h-9 rounded-lg bg-primary flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-primary-foreground" />
            </div>
            <span className="text-xl font-semibold tracking-tight">{t('app.name')}</span>
          </div>
          <p className="text-sm text-muted-foreground mt-1">{t('app.tagline')}</p>
        </div>

        <div className="relative z-10 space-y-8">
          {features.map((f) => (
            <div key={f.title} className="flex gap-4">
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0 mt-0.5">
                <f.icon className="w-5 h-5 text-primary" />
              </div>
              <div>
                <h3 className="font-medium text-sm">{f.title}</h3>
                <p className="text-sm text-muted-foreground mt-0.5">{f.desc}</p>
              </div>
            </div>
          ))}
        </div>

        <p className="relative z-10 text-xs text-muted-foreground">
          {t('auth.footer')}
        </p>
      </div>

      {/* Right form panel */}
      <div className="flex-1 flex items-center justify-center px-6 py-10 relative">
        <div className="absolute top-6 right-6">
          <LanguageSwitcher />
        </div>
        <div className="w-full max-w-[400px]">
          {/* Mobile logo */}
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div className="w-9 h-9 rounded-lg bg-primary flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-primary-foreground" />
            </div>
            <span className="text-xl font-semibold tracking-tight">{t('app.name')}</span>
          </div>

          <div className="mb-8">
            <h1 className="text-2xl font-semibold tracking-tight">
              {challengeToken
                ? t('auth.totpTitle')
                : isRegister
                  ? t('auth.createAccount')
                  : t('auth.welcomeBack')}
            </h1>
            <p className="text-sm text-muted-foreground mt-1.5">
              {challengeToken
                ? t('auth.totpSubtitle')
                : isRegister
                  ? t('auth.createAccountSubtitle')
                  : t('auth.signInSubtitle')}
            </p>
          </div>

          {challengeToken ? (
            <form onSubmit={handleTotpSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="totp">{t('auth.totpCode')}</Label>
                <Input
                  id="totp"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  maxLength={6}
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
                  placeholder="123 456"
                  autoFocus
                  required
                  className="font-mono tracking-[0.4em] text-center text-lg"
                />
              </div>

              {error && (
                <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5">
                  <p className="text-sm text-destructive">{error}</p>
                </div>
              )}

              <Button
                type="submit"
                className="w-full cursor-pointer"
                disabled={loading || totpCode.length !== 6}
              >
                {loading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    {t('auth.totpVerify')}
                    <ArrowRight className="w-4 h-4 ml-2" />
                  </>
                )}
              </Button>

              <button
                type="button"
                onClick={() => {
                  setChallengeToken(null);
                  setTotpCode('');
                  setError(null);
                }}
                className="w-full text-center text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
              >
                {t('common.back')}
              </button>
            </form>
          ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="username">{t('auth.username')}</Label>
              <Input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                onBlur={() => markTouched('username')}
                placeholder={t('auth.usernamePlaceholder')}
                required
                minLength={3}
                autoComplete="username"
              />
              {isRegister && touched.username && !usernameValid && (
                <p className="text-xs text-destructive">{t('auth.usernameMinError')}</p>
              )}
            </div>

            {isRegister && (
              <div className="space-y-1.5">
                <Label htmlFor="email">{t('auth.email')}</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onBlur={() => markTouched('email')}
                  placeholder={t('auth.emailPlaceholder')}
                  required
                  autoComplete="email"
                />
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="password">{t('auth.password')}</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onBlur={() => markTouched('password')}
                  placeholder={isRegister ? t('auth.passwordMinPlaceholder') : t('auth.passwordPlaceholder')}
                  required
                  minLength={8}
                  className="pr-10"
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                />
                <button
                  type="button"
                  tabIndex={-1}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {isRegister && touched.password && password.length > 0 && (
                <div className="space-y-1.5 pt-1">
                  <div className="flex gap-1">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <div
                        key={i}
                        className={`h-1 flex-1 rounded-full transition-colors ${
                          i <= strength.score ? strength.color : 'bg-muted'
                        }`}
                      />
                    ))}
                  </div>
                  <p className="text-xs text-muted-foreground">{strength.label}</p>
                </div>
              )}
            </div>

            {isRegister && (
              <div className="space-y-1.5">
                <Label htmlFor="confirmPassword">{t('auth.confirmPassword')}</Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirm ? 'text' : 'password'}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    onBlur={() => markTouched('confirmPassword')}
                    placeholder={t('auth.confirmPasswordPlaceholder')}
                    required
                    minLength={8}
                    className="pr-10"
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    tabIndex={-1}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                    onClick={() => setShowConfirm(!showConfirm)}
                  >
                    {showConfirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
                {touched.confirmPassword && confirmPassword.length > 0 && !passwordsMatch && (
                  <p className="text-xs text-destructive">{t('auth.passwordsNoMatch')}</p>
                )}
              </div>
            )}

            {error && (
              <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5">
                <p className="text-sm text-destructive">{error}</p>
              </div>
            )}

            <Button type="submit" className="w-full cursor-pointer" disabled={loading || !formValid}>
              {loading ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <>
                  {isRegister ? t('auth.createAccount') : t('auth.signIn')}
                  <ArrowRight className="w-4 h-4 ml-2" />
                </>
              )}
            </Button>
          </form>
          )}

          {!challengeToken && (
            <p className="text-center text-sm text-muted-foreground mt-6">
              {isRegister ? t('auth.haveAccount') : t('auth.noAccount')}{' '}
              <button
                type="button"
                className="text-primary hover:text-primary/80 font-medium transition-colors cursor-pointer"
                onClick={() => {
                  setIsRegister(!isRegister);
                  resetForm();
                }}
              >
                {isRegister ? t('auth.signIn') : t('auth.createAccount')}
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
