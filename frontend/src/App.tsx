import { Routes, Route, Navigate } from 'react-router-dom';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { PortfolioPage } from '@/pages/PortfolioPage';
import { PortfolioDetailPage } from '@/pages/PortfolioDetailPage';
import { BudgetPage } from '@/pages/BudgetPage';
import { BillsPage } from '@/pages/BillsPage';
import { AnalyticsPage } from '@/pages/AnalyticsPage';
import { PricesPage } from '@/pages/PricesPage';
import { AssetDetailPage } from '@/pages/AssetDetailPage';
import { AlertsPage } from '@/pages/AlertsPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { AppShell } from '@/components/layout/AppShell';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
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
  );
}
