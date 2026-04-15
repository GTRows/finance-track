import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/store/auth.store';
import { authApi } from '@/api/auth.api';
import {
  LayoutDashboard,
  Briefcase,
  Wallet,
  Receipt,
  TrendingUp,
  BarChart3,
  LineChart,
  Bell,
  LogOut,
  Settings,
  ChevronLeft,
  ChevronRight,
  Menu,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ThemeSwitcher } from './ThemeSwitcher';
import { NotificationBell } from './NotificationBell';
import { useLivePrices } from '@/hooks/useLivePrices';
import { useSettings } from '@/hooks/useSettings';
import { BackendStatusBanner } from './BackendStatusBanner';

export function AppShell() {
  useLivePrices();
  useSettings();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { user, refreshToken, clearAuth } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  const navItems = [
    { path: '/', label: t('nav.dashboard'), icon: LayoutDashboard },
    { path: '/portfolio', label: t('nav.portfolio'), icon: Briefcase },
    { path: '/budget', label: t('nav.budget'), icon: Wallet },
    { path: '/bills', label: t('nav.bills'), icon: Receipt },
    { path: '/analytics', label: t('nav.analytics'), icon: BarChart3 },
    { path: '/prices', label: t('nav.prices'), icon: LineChart },
    { path: '/alerts', label: t('nav.alerts'), icon: Bell },
  ];

  const handleLogout = async () => {
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken);
      } catch {
        // Clear local state regardless
      }
    }
    clearAuth();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-background flex">
      {/* Mobile backdrop */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/50 md:hidden"
          onClick={() => setMobileOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed left-0 top-0 bottom-0 z-30 flex flex-col bg-card border-r transition-[width,transform] duration-200',
          collapsed ? 'md:w-16' : 'md:w-56',
          'w-64',
          mobileOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        )}
      >
        {/* Logo */}
        <div className="h-14 flex items-center gap-2.5 px-4 border-b flex-shrink-0">
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center flex-shrink-0">
            <TrendingUp className="w-4 h-4 text-primary-foreground" />
          </div>
          <span
            className={cn(
              'font-semibold text-sm tracking-tight whitespace-nowrap',
              collapsed && 'md:hidden'
            )}
          >
            {t('app.name')}
          </span>
          <button
            type="button"
            onClick={() => setMobileOpen(false)}
            className="ml-auto md:hidden w-8 h-8 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer"
            aria-label={t('common.closeMenu')}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 py-3 px-2 space-y-0.5 overflow-y-auto">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.path}
                to={item.path}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 h-9 text-sm font-medium transition-colors cursor-pointer',
                  isActive
                    ? 'bg-primary/10 text-primary'
                    : 'text-muted-foreground hover:text-foreground hover:bg-accent'
                )}
                title={collapsed ? item.label : undefined}
              >
                <item.icon className="w-4 h-4 flex-shrink-0" />
                <span className={cn('whitespace-nowrap', collapsed && 'md:hidden')}>
                  {item.label}
                </span>
              </Link>
            );
          })}
        </nav>

        {/* Bottom section */}
        <div className="border-t px-2 py-3 space-y-0.5">
          <Link
            to="/settings"
            className={cn(
              'flex items-center gap-3 rounded-md px-3 h-9 text-sm font-medium transition-colors cursor-pointer',
              location.pathname === '/settings'
                ? 'bg-primary/10 text-primary'
                : 'text-muted-foreground hover:text-foreground hover:bg-accent'
            )}
            title={collapsed ? t('nav.settings') : undefined}
          >
            <Settings className="w-4 h-4 flex-shrink-0" />
            <span className={cn(collapsed && 'md:hidden')}>{t('nav.settings')}</span>
          </Link>

          <button
            onClick={handleLogout}
            className="flex items-center gap-3 rounded-md px-3 h-9 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer w-full"
            title={collapsed ? t('nav.signOut') : undefined}
          >
            <LogOut className="w-4 h-4 flex-shrink-0" />
            <span className={cn(collapsed && 'md:hidden')}>{t('nav.signOut')}</span>
          </button>
        </div>

        {/* Collapse toggle (desktop only) */}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="hidden md:flex absolute -right-3 top-[4.25rem] w-6 h-6 rounded-full border bg-card items-center justify-center text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
        >
          {collapsed ? <ChevronRight className="w-3 h-3" /> : <ChevronLeft className="w-3 h-3" />}
        </button>
      </aside>

      {/* Main content */}
      <div
        className={cn(
          'flex-1 flex flex-col min-w-0 transition-[margin] duration-200',
          collapsed ? 'md:ml-16' : 'md:ml-56'
        )}
      >
        {/* Top bar */}
        <header className="h-14 border-b bg-card/50 backdrop-blur-sm flex items-center justify-between gap-3 px-4 sm:px-6 sticky top-0 z-20">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="md:hidden w-9 h-9 rounded-md flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer"
            aria-label={t('common.openMenu')}
          >
            <Menu className="w-4 h-4" />
          </button>
          <div className="flex items-center gap-2 sm:gap-3 ml-auto">
            <NotificationBell />
            <ThemeSwitcher />
            <LanguageSwitcher />
            <div className="w-px h-5 bg-border hidden sm:block" />
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center">
                <span className="text-xs font-medium text-primary">
                  {user?.username?.charAt(0).toUpperCase()}
                </span>
              </div>
              <span className="text-sm text-muted-foreground hidden sm:block">{user?.username}</span>
            </div>
          </div>
        </header>

        <BackendStatusBanner />

        {/* Page content */}
        <main className="flex-1 p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
