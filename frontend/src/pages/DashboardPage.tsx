import { useAuthStore } from '@/store/auth.store';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

/** Placeholder dashboard page shown after login. */
export function DashboardPage() {
  const user = useAuthStore((s) => s.user);

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-3xl font-semibold">Hos geldiniz, {user?.username}</h1>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Net Varlik</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-mono text-muted-foreground">--</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Aylik Gelir</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-mono text-muted-foreground">--</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Aylik Gider</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-mono text-muted-foreground">--</p>
          </CardContent>
        </Card>
      </div>
      <p className="text-muted-foreground">
        Dashboard Phase 2'de tamamlanacak. Simdilik sisteme giris yapabildiginizi dogruladiniz.
      </p>
    </div>
  );
}
