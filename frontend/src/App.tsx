import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from '@/pages/LoginPage';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { AppShell } from '@/components/layout/AppShell';
import { RouteFallback } from '@/components/layout/RouteFallback';

const VerifyEmailPage = lazy(() =>
  import('@/pages/VerifyEmailPage').then((m) => ({ default: m.VerifyEmailPage })),
);
const ResetPasswordPage = lazy(() =>
  import('@/pages/ResetPasswordPage').then((m) => ({ default: m.ResetPasswordPage })),
);
const DashboardPage = lazy(() =>
  import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })),
);
const PortfolioPage = lazy(() =>
  import('@/pages/PortfolioPage').then((m) => ({ default: m.PortfolioPage })),
);
const PortfolioDetailPage = lazy(() =>
  import('@/pages/PortfolioDetailPage').then((m) => ({ default: m.PortfolioDetailPage })),
);
const BudgetPage = lazy(() =>
  import('@/pages/BudgetPage').then((m) => ({ default: m.BudgetPage })),
);
const BillsPage = lazy(() =>
  import('@/pages/BillsPage').then((m) => ({ default: m.BillsPage })),
);
const AnalyticsPage = lazy(() =>
  import('@/pages/AnalyticsPage').then((m) => ({ default: m.AnalyticsPage })),
);
const PricesPage = lazy(() =>
  import('@/pages/PricesPage').then((m) => ({ default: m.PricesPage })),
);
const AssetDetailPage = lazy(() =>
  import('@/pages/AssetDetailPage').then((m) => ({ default: m.AssetDetailPage })),
);
const AlertsPage = lazy(() =>
  import('@/pages/AlertsPage').then((m) => ({ default: m.AlertsPage })),
);
const SettingsPage = lazy(() =>
  import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);

export function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route
          element={
            <ProtectedRoute>
              <AppShell />
            </ProtectedRoute>
          }
        >
          <Route path="/" element={<DashboardPage />} />
          <Route path="/portfolio" element={<PortfolioPage />} />
          <Route path="/portfolio/:id" element={<PortfolioDetailPage />} />
          <Route path="/budget" element={<BudgetPage />} />
          <Route path="/bills" element={<BillsPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/prices" element={<PricesPage />} />
          <Route path="/prices/:id" element={<AssetDetailPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
