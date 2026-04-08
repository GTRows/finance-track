import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/auth.store';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

/** Redirects to /login if the user is not authenticated. */
export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const accessToken = useAuthStore((s) => s.accessToken);
  const refreshToken = useAuthStore((s) => s.refreshToken);

  if (!accessToken && !refreshToken) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
