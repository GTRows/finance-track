import { useTranslation } from 'react-i18next';
import { useAccounts } from '@/hooks/useAccounts';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';

interface AccountPickerProps {
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  label?: string;
  className?: string;
}

/** Shared shadcn-styled native select for picking an Account on transaction dialogs. */
export function AccountPicker({ value, onChange, label, className }: AccountPickerProps) {
  const { t } = useTranslation();
  const { data: accounts = [] } = useAccounts();

  return (
    <div className={cn('space-y-1.5', className)}>
      <Label>{label ?? t('accounts.label')}</Label>
      <select
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
        className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        <option value="">{t('accounts.noAccount')}</option>
        {accounts
          .filter((a) => !a.archived)
          .map((account) => (
            <option key={account.id} value={account.id}>
              {account.name} ({account.currency})
            </option>
          ))}
      </select>
    </div>
  );
}
