import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as RRD from 'react-router-dom';
import { LoginPage } from './LoginPage';
import { authApi } from '@/api/auth.api';
import { useAuthStore } from '@/store/auth.store';

const SECRET = 'pw-fixture';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
  }),
}));

vi.mock('@/api/auth.api', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    verifyTotp: vi.fn(),
  },
}));

const navigateSpy = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof RRD>('react-router-dom');
  return { ...actual, useNavigate: () => navigateSpy };
});

function renderLogin() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    Object.values(authApi).forEach((fn) => vi.mocked(fn).mockReset());
    navigateSpy.mockReset();
    useAuthStore.setState({ user: null, accessToken: null, refreshToken: null });
  });

  it('renders the sign-in title and form fields by default', () => {
    renderLogin();

    expect(screen.getByRole('heading', { name: 'auth.welcomeBack' })).toBeDefined();
    expect(screen.getByLabelText('auth.username')).toBeDefined();
    expect(screen.getByLabelText('auth.password')).toBeDefined();
    expect(screen.getByRole('button', { name: /auth\.signIn/ })).toBeDefined();
  });

  it('logs in successfully and stores the tokens', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      user: {
        id: 'u1',
        username: 'ali',
        email: 'a@b',
        role: 'USER',
        emailVerified: true,
        createdAt: '2026-01-01',
      },
      accessToken: 'access',
      refreshToken: 'refresh',
      requiresTotp: false,
    } as never);

    renderLogin();
    fireEvent.change(screen.getByLabelText('auth.username'), { target: { value: 'ali' } });
    fireEvent.change(screen.getByLabelText('auth.password'), { target: { value: SECRET } });
    fireEvent.submit(screen.getByLabelText('auth.username').closest('form')!);

    await waitFor(() =>
      expect(authApi.login).toHaveBeenCalledWith({ username: 'ali', password: SECRET }),
    );
    await waitFor(() => expect(useAuthStore.getState().accessToken).toBe('access'));
    expect(navigateSpy).toHaveBeenCalledWith('/', { replace: true });
  });

  it('shows the TOTP step when login challenges with totp', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      requiresTotp: true,
      totpChallengeToken: 'chal-1',
    } as never);

    renderLogin();
    fireEvent.change(screen.getByLabelText('auth.username'), { target: { value: 'ali' } });
    fireEvent.change(screen.getByLabelText('auth.password'), { target: { value: SECRET } });
    fireEvent.submit(screen.getByLabelText('auth.username').closest('form')!);

    expect(await screen.findByRole('heading', { name: 'auth.totpTitle' })).toBeDefined();
    expect(screen.getByLabelText('auth.totpCode')).toBeDefined();
  });

  it('surfaces the api error message on failure', async () => {
    vi.mocked(authApi.login).mockRejectedValueOnce({
      response: { data: { error: 'Invalid credentials' } },
    });

    renderLogin();
    fireEvent.change(screen.getByLabelText('auth.username'), { target: { value: 'ali' } });
    fireEvent.change(screen.getByLabelText('auth.password'), { target: { value: 'wrong' } });
    fireEvent.submit(screen.getByLabelText('auth.username').closest('form')!);

    expect(await screen.findByText('Invalid credentials')).toBeDefined();
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('toggles to register mode and adds the email + confirm fields', () => {
    renderLogin();

    const toggleButtons = screen.getAllByRole('button', { name: 'auth.createAccount' });
    fireEvent.click(toggleButtons[toggleButtons.length - 1]);

    expect(screen.getByRole('heading', { name: 'auth.createAccount' })).toBeDefined();
    expect(screen.getByLabelText('auth.email')).toBeDefined();
    expect(screen.getByLabelText('auth.confirmPassword')).toBeDefined();
  });
});
