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
  LogOut,
  Settings,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/utils';
import { LanguageSwitcher } from './LanguageSwitcher';
import { ThemeSwitcher } from './ThemeSwitcher';
import { useLivePrices } from '@/hooks/useLivePrices';

export function AppShell() {
  useLivePrices();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { user, refreshToken, clearAuth } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);

  const navItems = [
    { path: '/', label: t('nav.dashboard'), icon: LayoutDashboard },
    { path: '/portfolio', label: t('nav.portfolio'), icon: Briefcase },
    { path: '/budget', label: t('nav.budget'), icon: Wallet },
    { path: '/bills', label: t('nav.bills'), icon: Receipt },
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
      {/* Sidebar */}
      <aside
        className={cn(
          'fixed left-0 top-0 bottom-0 z-30 flex flex-col bg-card border-r transition-[width] duration-200',
          collapsed ? 'w-16' : 'w-56'
        )}
      >
        {/* Logo */}
        <div className="h-14 flex items-center gap-2.5 px-4 border-b flex-shrink-0">
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center flex-shrink-0">
            <TrendingUp className="w-4 h-4 text-primary-foreground" />
          </div>
          {!collapsed && (
            <span className="font-semibold text-sm tracking-tight whitespace-nowrap">
              {t('app.name')}
            </span>
          )}
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
                {!collapsed && <span className="whitespace-nowrap">{item.label}</span>}
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
            {!collapsed && <span>{t('nav.settings')}</span>}
          </Link>

          <button
            onClick={handleLogout}
            className="flex items-center gap-3 rounded-md px-3 h-9 text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors cursor-pointer w-full"
            title={collapsed ? t('nav.signOut') : undefined}
          >
            <LogOut className="w-4 h-4 flex-shrink-0" />
            {!collapsed && <span>{t('nav.signOut')}</span>}
          </button>
        </div>

        {/* Collapse toggle */}
        <button
          onClick={() => setCollapsed(!collapsed)}
          className="absolute -right-3 top-[4.25rem] w-6 h-6 rounded-full border bg-card flex items-center justify-center text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
        >
          {collapsed ? <ChevronRight className="w-3 h-3" /> : <ChevronLeft className="w-3 h-3" />}
        </button>
      </aside>

      {/* Main content */}
      <div className={cn('flex-1 flex flex-col transition-[margin] duration-200', collapsed ? 'ml-16' : 'ml-56')}>
        {/* Top bar */}
        <header className="h-14 border-b bg-card/50 backdrop-blur-sm flex items-center justify-between px-6 sticky top-0 z-20">
          <div />
          <div className="flex items-center gap-3">
            <ThemeSwitcher />
            <LanguageSwitcher />
            <div className="w-px h-5 bg-border" />
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

        {/* Page content */}
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
