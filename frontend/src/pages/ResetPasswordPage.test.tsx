import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { ResetPasswordPage } from './ResetPasswordPage';
import { authApi } from '@/api/auth.api';

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof I18N>();
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { changeLanguage: vi.fn(), resolvedLanguage: 'en' },
    }),
  };
});

vi.mock('@/api/auth.api', () => ({
  authApi: {
    requestPasswordReset: vi.fn(),
    confirmPasswordReset: vi.fn(),
  },
}));

function renderPage(initialPath = '/reset-password') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ResetPasswordPage />
    </MemoryRouter>,
  );
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.requestPasswordReset).mockReset();
    vi.mocked(authApi.confirmPasswordReset).mockReset();
  });

  it('renders the request panel without a token', () => {
    renderPage();
    expect(screen.getByText('auth.resetRequestTitle')).toBeDefined();
    expect(screen.getByLabelText('auth.email')).toBeDefined();
  });

  it('shows success after a successful reset request', async () => {
    vi.mocked(authApi.requestPasswordReset).mockResolvedValueOnce(undefined as never);
    renderPage();

    fireEvent.change(screen.getByLabelText('auth.email'), { target: { value: 'u@example.com' } });
    fireEvent.submit(screen.getByLabelText('auth.email').closest('form')!);

    await waitFor(() =>
      expect(authApi.requestPasswordReset).toHaveBeenCalledWith('u@example.com'),
    );
    expect(await screen.findByText('auth.resetRequestSent')).toBeDefined();
  });

  it('renders the confirm panel when a token is in the URL', () => {
    renderPage('/reset-password?token=abc123');
    expect(screen.getByText('auth.resetConfirmTitle')).toBeDefined();
    expect(screen.getByLabelText('settings.passwordNew')).toBeDefined();
  });
});
