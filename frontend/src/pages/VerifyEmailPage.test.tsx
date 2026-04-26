import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type * as I18N from 'react-i18next';
import { VerifyEmailPage } from './VerifyEmailPage';
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
  authApi: { confirmEmailVerification: vi.fn() },
}));

function renderPage(initialPath = '/verify-email') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <VerifyEmailPage />
    </MemoryRouter>,
  );
}

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.confirmEmailVerification).mockReset();
    vi.mocked(authApi.confirmEmailVerification).mockResolvedValue(undefined as never);
  });

  it('shows the missing-token error when no token is in the URL', async () => {
    renderPage();
    expect(await screen.findByText('auth.verifyErrorTitle')).toBeDefined();
    expect(authApi.confirmEmailVerification).not.toHaveBeenCalled();
  });

  it('shows success when the verification call resolves', async () => {
    renderPage('/verify-email?token=abc');

    expect(await screen.findByText('auth.verifySuccessTitle')).toBeDefined();
    expect(authApi.confirmEmailVerification).toHaveBeenCalledWith('abc');
  });

  it('shows the api error message when verification fails', async () => {
    vi.mocked(authApi.confirmEmailVerification).mockReset();
    vi.mocked(authApi.confirmEmailVerification).mockRejectedValue({
      response: { data: { error: 'Token expired' } },
    });
    renderPage('/verify-email?token=stale');

    expect(await screen.findByText('Token expired')).toBeDefined();
  });
});
