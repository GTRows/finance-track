import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useCreateAccount, useUpdateAccount } from '@/hooks/useAccounts';
import type {
  Account,
  AccountType,
  CreateAccountRequest,
  UpdateAccountRequest,
} from '@/types/account.types';
import { Check, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AxiosError } from 'axios';
import type { ApiError } from '@/types/auth.types';
import { ACCOUNT_TYPES } from './account-types';

interface AddAccountDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Existing account to edit; absent for create flow. */
  editing?: Account | null;
}

export function AddAccountDialog({ open, onOpenChange, editing }: AddAccountDialogProps) {
  const { t } = useTranslation();
  const createAccount = useCreateAccount();
  const updateAccount = useUpdateAccount();

  const [name, setName] = useState('');
  const [type, setType] = useState<AccountType>('BANK_CHECKING');
  const [currency, setCurrency] = useState('TRY');
  const [institution, setInstitution] = useState('');
  const [accountNumberSuffix, setAccountNumberSuffix] = useState('');
  const [notes, setNotes] = useState('');
  const [balance, setBalance] = useState('');
  const [error, setError] = useState<string | null>(null);

  const isEditing = !!editing;
  const pending = createAccount.isPending || updateAccount.isPending;

  useEffect(() => {
    if (open && editing) {
      setName(editing.name);
      setType(editing.type);
      setCurrency(editing.currency);
      setInstitution(editing.institution ?? '');
      setAccountNumberSuffix(editing.accountNumberSuffix ?? '');
      setNotes(editing.notes ?? '');
      setBalance(editing.currentBalance);
      setError(null);
    } else if (open && !editing) {
      reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing?.id]);

  const reset = () => {
    setName('');
    setType('BANK_CHECKING');
    setCurrency('TRY');
    setInstitution('');
    setAccountNumberSuffix('');
    setNotes('');
    setBalance('');
    setError(null);
  };

  const handleClose = (next: boolean) => {
    if (!next) reset();
    onOpenChange(next);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const trimmedName = name.trim();
    if (trimmedName.length === 0) {
      setError(t('accounts.errors.nameRequired'));
      return;
    }

    const normalizedCurrency = currency.trim().toUpperCase();
    if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
      setError(t('accounts.errors.invalidCurrency'));
      return;
    }

    if (accountNumberSuffix && !/^[0-9]{1,16}$/.test(accountNumberSuffix.trim())) {
      setError(t('accounts.errors.invalidSuffix'));
      return;
    }

    try {
      if (isEditing && editing) {
        const request: UpdateAccountRequest = {
          name: trimmedName,
          currency: normalizedCurrency,
          institution: institution.trim() || null,
          accountNumberSuffix: accountNumberSuffix.trim() || null,
          notes: notes.trim() || null,
          currentBalance: balance.trim() === '' ? '0' : balance.trim(),
        };
        await updateAccount.mutateAsync({ id: editing.id, request });
      } else {
        const request: CreateAccountRequest = {
          name: trimmedName,
          type,
          currency: normalizedCurrency,
          institution: institution.trim() || null,
          accountNumberSuffix: accountNumberSuffix.trim() || null,
          notes: notes.trim() || null,
          openingBalance: balance.trim() === '' ? null : balance.trim(),
        };
        await createAccount.mutateAsync(request);
      }
      handleClose(false);
    } catch (err) {
      const axiosError = err as AxiosError<ApiError>;
      setError(axiosError.response?.data?.error || t('accounts.errors.failedToSave'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? t('accounts.dialog.editTitle') : t('accounts.dialog.createTitle')}
          </DialogTitle>
          <DialogDescription>
            {isEditing
              ? t('accounts.dialog.editDescription')
              : t('accounts.dialog.createDescription')}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="account-name">{t('accounts.form.name')}</Label>
            <Input
              id="account-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder={t('accounts.form.namePlaceholder')}
              maxLength={100}
              autoFocus
              required
            />
          </div>

          {!isEditing && (
            <div className="space-y-1.5">
              <Label>{t('accounts.form.type')}</Label>
              <div className="grid grid-cols-3 gap-2">
                {ACCOUNT_TYPES.map((opt) => {
                  const Icon = opt.icon;
                  const active = type === opt.value;
                  const label = t(opt.labelKey);
                  return (
                    <button
                      key={opt.value}
                      type="button"
                      onClick={() => setType(opt.value)}
                      title={label}
                      className={cn(
                        'relative flex flex-col items-center gap-1.5 rounded-md border p-2.5 text-center transition-colors cursor-pointer',
                        active
                          ? 'border-primary bg-primary/5'
                          : 'border-input hover:border-muted-foreground/30 hover:bg-accent'
                      )}
                    >
                      {active && (
                        <span className="absolute top-1 right-1 w-3.5 h-3.5 rounded-full bg-primary flex items-center justify-center">
                          <Check className="w-2.5 h-2.5 text-primary-foreground" strokeWidth={3} />
                        </span>
                      )}
                      <div
                        className={cn(
                          'w-7 h-7 rounded-md flex items-center justify-center transition-colors',
                          active ? 'bg-primary/15 text-primary' : 'bg-muted text-muted-foreground'
                        )}
                      >
                        <Icon className="w-4 h-4" />
                      </div>
                      <span className="text-[11px] font-medium leading-tight">{label}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="account-currency">{t('accounts.form.currency')}</Label>
              <Input
                id="account-currency"
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                placeholder="TRY"
                maxLength={3}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="account-balance">
                {isEditing
                  ? t('accounts.form.currentBalance')
                  : t('accounts.form.openingBalance')}
              </Label>
              <Input
                id="account-balance"
                value={balance}
                onChange={(e) => setBalance(e.target.value)}
                placeholder="0.00"
                inputMode="decimal"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="account-institution">{t('accounts.form.institution')}</Label>
              <Input
                id="account-institution"
                value={institution}
                onChange={(e) => setInstitution(e.target.value)}
                placeholder={t('accounts.form.institutionPlaceholder')}
                maxLength={100}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="account-suffix">{t('accounts.form.accountNumberSuffix')}</Label>
              <Input
                id="account-suffix"
                value={accountNumberSuffix}
                onChange={(e) => setAccountNumberSuffix(e.target.value.replace(/\D/g, ''))}
                placeholder="1234"
                maxLength={16}
                inputMode="numeric"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="account-notes">{t('accounts.form.notes')}</Label>
            <Textarea
              id="account-notes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={t('accounts.form.notesPlaceholder')}
              maxLength={1000}
              rows={3}
            />
          </div>

          {error && (
            <div className="rounded-md bg-destructive/10 border border-destructive/20 px-3 py-2.5">
              <p className="text-sm text-destructive">{error}</p>
            </div>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              className="cursor-pointer"
              onClick={() => handleClose(false)}
              disabled={pending}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" className="cursor-pointer" disabled={pending}>
              {pending ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  {t('common.saving')}
                </>
              ) : isEditing ? (
                t('common.save')
              ) : (
                t('accounts.dialog.createSubmit')
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
