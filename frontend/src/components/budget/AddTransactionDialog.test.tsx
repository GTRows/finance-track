import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { AddTransactionDialog } from './AddTransactionDialog';
import { tagsApi } from '@/api/tags.api';
import { createWrapper } from '@/test-utils/queryWrapper';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}));

vi.mock('@/api/tags.api', () => ({
  tagsApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}));

const incomeCats = [{ id: 'c-inc', name: 'Salary', color: '#0f0' }] as never;
const expenseCats = [{ id: 'c-exp', name: 'Rent', color: '#f00' }] as never;

function renderDialog(onSubmit = vi.fn()) {
  const { Wrapper } = createWrapper();
  vi.mocked(tagsApi.list).mockResolvedValue([]);
  return {
    ...render(
      <AddTransactionDialog
        incomeCategories={incomeCats}
        expenseCategories={expenseCats}
        onSubmit={onSubmit}
        isPending={false}
      />,
      { wrapper: Wrapper },
    ),
    onSubmit,
  };
}

describe('AddTransactionDialog', () => {
  beforeEach(() => {
    vi.mocked(tagsApi.list).mockReset();
    vi.mocked(tagsApi.create).mockReset();
  });

  it('renders the trigger button', () => {
    renderDialog();
    expect(screen.getByRole('button', { name: /budget\.addTransaction/ })).toBeDefined();
  });

  it('opens the dialog and shows EXPENSE/INCOME toggles', () => {
    renderDialog();
    fireEvent.click(screen.getAllByRole('button', { name: /budget\.addTransaction/ })[0]);

    expect(screen.getByRole('button', { name: 'budget.income' })).toBeDefined();
    expect(screen.getByRole('button', { name: 'budget.expenses' })).toBeDefined();
  });

  it('calls onSubmit with the parsed amount and txnType', () => {
    const { onSubmit } = renderDialog();
    const triggers = screen.getAllByRole('button', { name: /budget\.addTransaction/ });
    fireEvent.click(triggers[0]);

    const amountInputs = screen.getAllByPlaceholderText('0.00');
    amountInputs.forEach((el) => fireEvent.change(el, { target: { value: '150.50' } }));

    const saveButtons = screen.getAllByRole('button', { name: 'common.save' });
    const saveBtn = saveButtons[saveButtons.length - 1] as HTMLButtonElement;
    fireEvent.click(saveBtn);

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit.mock.calls[0][0]).toMatchObject({
      txnType: 'EXPENSE',
      amount: 150.5,
    });
  });

  it('does not call onSubmit when amount is zero', () => {
    renderDialog();
    fireEvent.click(screen.getAllByRole('button', { name: /budget\.addTransaction/ })[0]);

    const amountInputs = screen.getAllByPlaceholderText('0.00');
    fireEvent.change(amountInputs[0], { target: { value: '0' } });

    const saveButtons = screen.getAllByRole('button', { name: 'common.save' });
    const saveBtn = saveButtons[0] as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(true);
  });
});
