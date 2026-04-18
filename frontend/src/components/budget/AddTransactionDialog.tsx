import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Plus, TrendingUp, TrendingDown, Tag as TagIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { BudgetTxnType, Category, CreateTransactionRequest } from '@/types/budget.types';
import { useCreateTag, useTags } from '@/hooks/useTags';

interface AddTransactionDialogProps {
  incomeCategories: Category[];
  expenseCategories: Category[];
  onSubmit: (req: CreateTransactionRequest) => void;
  isPending: boolean;
}

export function AddTransactionDialog({
  incomeCategories,
  expenseCategories,
  onSubmit,
  isPending,
}: AddTransactionDialogProps) {
  const { t } = useTranslation();
  const { data: allTags } = useTags();
  const createTag = useCreateTag();

  const [open, setOpen] = useState(false);
  const [txnType, setTxnType] = useState<BudgetTxnType>('EXPENSE');
  const [amount, setAmount] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [txnDate, setTxnDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [newTagName, setNewTagName] = useState('');

  const categories = txnType === 'INCOME' ? incomeCategories : expenseCategories;

  const reset = () => {
    setTxnType('EXPENSE');
    setAmount('');
    setCategoryId('');
    setDescription('');
    setTxnDate(new Date().toISOString().slice(0, 10));
    setSelectedTagIds([]);
    setNewTagName('');
  };

  const toggleTag = (id: string) => {
    setSelectedTagIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  const handleCreateTag = async () => {
    const name = newTagName.trim();
    if (!name) return;
    try {
      const created = await createTag.mutateAsync({ name });
      setSelectedTagIds((prev) => [...prev, created.id]);
      setNewTagName('');
    } catch {
      // validation / duplicate — surface via toast elsewhere
    }
  };

  const handleSubmit = () => {
    const parsed = parseFloat(amount);
    if (!parsed || parsed <= 0) return;

    onSubmit({
      txnType,
      amount: parsed,
      categoryId: categoryId || undefined,
      description: description || undefined,
      txnDate,
      isRecurring: false,
      tagIds: selectedTagIds.length > 0 ? selectedTagIds : undefined,
    });
    reset();
    setOpen(false);
  };

  return (
    <Dialog open={open} onOpenChange={(v) => { setOpen(v); if (!v) reset(); }}>
      <DialogTrigger asChild>
        <Button className="cursor-pointer">
          <Plus className="w-4 h-4 mr-2" />
          {t('budget.addTransaction')}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-[460px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('budget.addTransaction')}</DialogTitle>
        </DialogHeader>

        <div className="space-y-5 pt-2">
          {/* Type toggle */}
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => { setTxnType('INCOME'); setCategoryId(''); }}
              className={cn(
                'flex items-center justify-center gap-2 rounded-md border px-3 py-2.5 text-sm font-medium transition-colors cursor-pointer',
                txnType === 'INCOME'
                  ? 'border-emerald-500/50 bg-emerald-500/10 text-emerald-400'
                  : 'border-border text-muted-foreground hover:bg-accent'
              )}
            >
              <TrendingUp className="w-4 h-4" />
              {t('budget.income')}
            </button>
            <button
              type="button"
              onClick={() => { setTxnType('EXPENSE'); setCategoryId(''); }}
              className={cn(
                'flex items-center justify-center gap-2 rounded-md border px-3 py-2.5 text-sm font-medium transition-colors cursor-pointer',
                txnType === 'EXPENSE'
                  ? 'border-red-500/50 bg-red-500/10 text-red-400'
                  : 'border-border text-muted-foreground hover:bg-accent'
              )}
            >
              <TrendingDown className="w-4 h-4" />
              {t('budget.expenses')}
            </button>
          </div>

          {/* Amount */}
          <div className="space-y-1.5">
            <Label>{t('budget.amount')}</Label>
            <div className="relative">
              <Input
                type="number"
                step="0.01"
                min="0"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="pr-10 font-mono tabular-nums"
              />
              <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground">
                TRY
              </span>
            </div>
          </div>

          {/* Category */}
          <div className="space-y-1.5">
            <Label>{t('budget.category')}</Label>
            {categories.length === 0 ? (
              <p className="text-xs text-muted-foreground py-2">{t('budget.noCategories')}</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {categories.map((cat) => (
                  <button
                    key={cat.id}
                    type="button"
                    onClick={() => setCategoryId(categoryId === cat.id ? '' : cat.id)}
                    className={cn(
                      'flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors cursor-pointer',
                      categoryId === cat.id
                        ? 'border-primary/50 bg-primary/10 text-primary'
                        : 'border-border text-muted-foreground hover:bg-accent'
                    )}
                  >
                    {cat.color && (
                      <span
                        className="w-2 h-2 rounded-full flex-shrink-0"
                        style={{ backgroundColor: cat.color }}
                      />
                    )}
                    {cat.name}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Tags */}
          <div className="space-y-1.5">
            <Label className="flex items-center gap-1.5">
              <TagIcon className="w-3.5 h-3.5" />
              {t('tag.tags')}
            </Label>

            {allTags && allTags.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {allTags.map((tag) => {
                  const selected = selectedTagIds.includes(tag.id);
                  return (
                    <button
                      key={tag.id}
                      type="button"
                      onClick={() => toggleTag(tag.id)}
                      className={cn(
                        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors cursor-pointer',
                        selected
                          ? 'border-sky-500/50 bg-sky-500/10 text-sky-300'
                          : 'border-border text-muted-foreground hover:bg-accent'
                      )}
                    >
                      <span
                        className="w-1.5 h-1.5 rounded-full"
                        style={{ backgroundColor: tag.color ?? '#64748b' }}
                      />
                      {tag.name}
                    </button>
                  );
                })}
              </div>
            )}

            <div className="flex gap-1.5 pt-1">
              <Input
                placeholder={t('tag.newTagPlaceholder')}
                value={newTagName}
                onChange={(e) => setNewTagName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleCreateTag();
                  }
                }}
                className="h-8 text-xs"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCreateTag}
                disabled={!newTagName.trim() || createTag.isPending}
                className="h-8 px-3 cursor-pointer"
              >
                {t('common.add')}
              </Button>
            </div>
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <Label>{t('budget.description')}</Label>
            <Input
              placeholder={t('budget.descriptionPlaceholder')}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          {/* Date */}
          <div className="space-y-1.5">
            <Label>{t('budget.date')}</Label>
            <Input
              type="date"
              value={txnDate}
              onChange={(e) => setTxnDate(e.target.value)}
            />
          </div>

          <Button
            onClick={handleSubmit}
            disabled={isPending || !amount || parseFloat(amount) <= 0}
            className="w-full cursor-pointer"
          >
            {isPending ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

