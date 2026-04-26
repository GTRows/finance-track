import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AddPortfolioDialog } from './AddPortfolioDialog';
import { portfolioApi } from '@/api/portfolio.api';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}));

vi.mock('@/api/portfolio.api', () => ({
  portfolioApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('AddPortfolioDialog', () => {
  beforeEach(() => {
    vi.mocked(portfolioApi.create).mockReset();
  });

  it('renders title, name input, and submit button when open', () => {
    const { Wrapper } = createWrapper();
    render(<AddPortfolioDialog open onOpenChange={() => undefined} />, { wrapper: Wrapper });

    expect(screen.getByRole('heading', { name: 'portfolio.dialogTitle' })).toBeDefined();
    expect(screen.getByLabelText('portfolio.nameLabel')).toBeDefined();
    expect(screen.getByRole('button', { name: 'portfolio.createPortfolio' })).toBeDefined();
  });

  it('shows the validation error when name is whitespace-only', async () => {
    const { Wrapper } = createWrapper();
    render(<AddPortfolioDialog open onOpenChange={() => undefined} />, { wrapper: Wrapper });

    const nameInput = screen.getByLabelText('portfolio.nameLabel') as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: '   ' } });
    fireEvent.submit(nameInput.closest('form')!);

    expect(await screen.findByText('portfolio.nameRequired')).toBeDefined();
    expect(portfolioApi.create).not.toHaveBeenCalled();
  });

  it('submits the form and calls the create api', async () => {
    vi.mocked(portfolioApi.create).mockResolvedValueOnce({ id: 'p1', name: 'Main' } as never);
    const { Wrapper } = createWrapper();
    render(<AddPortfolioDialog open onOpenChange={() => undefined} />, { wrapper: Wrapper });

    const nameInput = screen.getByLabelText('portfolio.nameLabel') as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: 'Main' } });
    fireEvent.submit(nameInput.closest('form')!);

    await waitFor(() =>
      expect(portfolioApi.create).toHaveBeenCalledWith({
        name: 'Main',
        type: 'INDIVIDUAL',
        description: undefined,
      }),
    );
  });

  it('surfaces the api error message on rejection', async () => {
    vi.mocked(portfolioApi.create).mockRejectedValueOnce({
      response: { data: { error: 'Duplicate portfolio name' } },
    });
    const { Wrapper } = createWrapper();
    render(<AddPortfolioDialog open onOpenChange={() => undefined} />, { wrapper: Wrapper });

    const nameInput = screen.getByLabelText('portfolio.nameLabel') as HTMLInputElement;
    fireEvent.change(nameInput, { target: { value: 'Main' } });
    fireEvent.submit(nameInput.closest('form')!);

    expect(await screen.findByText('Duplicate portfolio name')).toBeDefined();
  });
});
