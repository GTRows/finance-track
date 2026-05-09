import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import type * as I18N from 'react-i18next';
import { TransactionRow } from './TransactionRow';
import type { BudgetTransaction } from '@/types/budget.types';
import { createWrapper } from '@/test-utils/queryWrapper';

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

vi.mock('@/api/receipt.api', () => ({
  receiptApi: {
    signedUrl: vi.fn(),
    upload: vi.fn(),
    remove: vi.fn(),
  },
}));

function makeTxn(overrides: Partial<BudgetTransaction> = {}): BudgetTransaction {
  return {
    id: 'txn-1',
    txnType: 'EXPENSE',
    amount: 250,
    currency: 'TRY',
    originalAmount: null,
    originalCurrency: null,
    categoryId: 'cat-1',
    categoryName: 'Groceries',
    categoryColor: '#22c55e',
    description: 'Migros run',
    txnDate: '2026-05-09',
    recurring: false,
    tags: null,
    hasReceipt: false,
    ocrStatus: null,
    ocrText: null,
    createdAt: '2026-05-09T10:00:00Z',
    accountId: null,
    accountName: null,
    ...overrides,
  };
}

function renderRow(props: Partial<Parameters<typeof TransactionRow>[0]> = {}) {
  const onToggleSelect = vi.fn();
  const onDelete = vi.fn();
  const { Wrapper } = createWrapper();
  render(
    <TransactionRow
      txn={props.txn ?? makeTxn()}
      selected={props.selected ?? false}
      anySelected={props.anySelected ?? false}
      month={props.month ?? '2026-05'}
      locale={props.locale ?? 'en-US'}
      onToggleSelect={props.onToggleSelect ?? onToggleSelect}
      onDelete={props.onDelete ?? onDelete}
    />,
    { wrapper: Wrapper },
  );
  return { onToggleSelect, onDelete };
}

describe('TransactionRow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the description and signs an EXPENSE amount with a leading minus', () => {
    renderRow({ txn: makeTxn({ txnType: 'EXPENSE', amount: 250, description: 'Migros run' }) });

    expect(screen.getByText('Migros run')).toBeDefined();
    const amountNode = screen.getByText((_, el) => {
      if (!el || el.tagName !== 'SPAN') return false;
      const text = (el.textContent ?? '').trim();
      if (text.length === 0) return false;
      if (el.children.length > 0) return false;
      return text.startsWith('-') && text.includes('250');
    });
    expect(amountNode).toBeDefined();
  });

  it('calls onToggleSelect with the transaction id when the selection checkbox is clicked', () => {
    const onToggleSelect = vi.fn();
    renderRow({ onToggleSelect });

    const selectButton = screen.getByTitle('budget.bulk.select');
    fireEvent.click(selectButton);

    expect(onToggleSelect).toHaveBeenCalledWith('txn-1');
  });

  it('calls onDelete with the transaction id when the delete button is clicked', () => {
    const onDelete = vi.fn();
    renderRow({ onDelete });

    const deleteButton = screen.getByTitle('common.delete');
    fireEvent.click(deleteButton);

    expect(onDelete).toHaveBeenCalledWith('txn-1');
  });
});
