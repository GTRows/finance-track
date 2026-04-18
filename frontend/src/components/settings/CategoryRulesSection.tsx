import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Wand2, Trash2, Check, X, Pencil, ArrowDownCircle, ArrowUpCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useCategoryRules,
  useCreateCategoryRule,
  useUpdateCategoryRule,
  useDeleteCategoryRule,
} from '@/hooks/useCategoryRules';
import { useCategories } from '@/hooks/useBudget';
import type { CategoryRule, TxnType, UpsertCategoryRuleRequest } from '@/types/category-rule.types';

export function CategoryRulesSection() {
  const { t } = useTranslation();
  const { data: rules = [] } = useCategoryRules();
  const { data: categories } = useCategories();
  const createRule = useCreateCategoryRule();
  const updateRule = useUpdateCategoryRule();
  const deleteRule = useDeleteCategoryRule();

  const expenseCats = categories?.expense ?? [];
  const incomeCats = categories?.income ?? [];

  const [newPattern, setNewPattern] = useState('');
  const [newType, setNewType] = useState<TxnType>('EXPENSE');
  const [newCategoryId, setNewCategoryId] = useState<string>('');

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPattern, setEditPattern] = useState('');
  const [editType, setEditType] = useState<TxnType>('EXPENSE');
  const [editCategoryId, setEditCategoryId] = useState<string>('');
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const categoriesFor = (type: TxnType) => (type === 'INCOME' ? incomeCats : expenseCats);

  const canCreate = useMemo(
    () => newPattern.trim().length > 0 && !!newCategoryId,
    [newPattern, newCategoryId],
  );

  const handleCreate = async () => {
    const pattern = newPattern.trim();
    if (!pattern || !newCategoryId) return;
    const req: UpsertCategoryRuleRequest = {
      pattern,
      categoryId: newCategoryId,
      txnType: newType,
    };
    try {
      await createRule.mutateAsync(req);
      setNewPattern('');
      setNewCategoryId('');
    } catch {
      // ignored
    }
  };

  const startEdit = (rule: CategoryRule) => {
    setEditingId(rule.id);
    setEditPattern(rule.pattern);
    setEditType(rule.txnType);
    setEditCategoryId(rule.categoryId);
  };

  const submitEdit = async () => {
    if (!editingId) return;
    const pattern = editPattern.trim();
    if (!pattern || !editCategoryId) return;
    try {
      await updateRule.mutateAsync({
        id: editingId,
        req: { pattern, categoryId: editCategoryId, txnType: editType },
      });
      setEditingId(null);
    } catch {
      // ignored
    }
  };

  const confirmDelete = async (id: string) => {
    if (pendingDelete !== id) {
      setPendingDelete(id);
      setTimeout(() => setPendingDelete((v) => (v === id ? null : v)), 3000);
      return;
    }
    await deleteRule.mutateAsync(id);
    setPendingDelete(null);
  };

  return (
    <div className="space-y-4">
      {/* Add new */}
      <div className="flex flex-wrap items-center gap-2">
        <TypePicker
          value={newType}
          onChange={(v) => {
            setNewType(v);
            setNewCategoryId('');
          }}
        />
        <Input
          value={newPattern}
          onChange={(e) => setNewPattern(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleCreate();
            }
          }}
          placeholder={t('categoryRules.patternPlaceholder')}
          className="h-9 w-[220px]"
        />
        <CategorySelect
          value={newCategoryId}
          onChange={setNewCategoryId}
          options={categoriesFor(newType)}
          placeholder={t('categoryRules.selectCategory')}
        />
        <Button
          onClick={handleCreate}
          disabled={!canCreate || createRule.isPending}
          size="sm"
          className="cursor-pointer"
        >
          {t('common.add')}
        </Button>
      </div>

      {/* List */}
      {rules.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-8 text-center border border-dashed border-border rounded-md">
          <div className="w-10 h-10 rounded-lg bg-muted flex items-center justify-center mb-3">
            <Wand2 className="w-5 h-5 text-muted-foreground" />
          </div>
          <p className="text-sm text-muted-foreground">{t('categoryRules.empty')}</p>
        </div>
      ) : (
        <ul className="divide-y divide-border border border-border rounded-md overflow-hidden">
          {rules.map((rule) => {
            const editing = editingId === rule.id;
            return (
              <li
                key={rule.id}
                className="flex items-center gap-3 px-3 py-2 hover:bg-accent/30 transition-colors"
              >
                {editing ? (
                  <>
                    <TypePicker
                      value={editType}
                      onChange={(v) => {
                        setEditType(v);
                        setEditCategoryId('');
                      }}
                    />
                    <Input
                      value={editPattern}
                      onChange={(e) => setEditPattern(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') submitEdit();
                        if (e.key === 'Escape') setEditingId(null);
                      }}
                      className="h-8 flex-1 min-w-[160px]"
                    />
                    <CategorySelect
                      value={editCategoryId}
                      onChange={setEditCategoryId}
                      options={categoriesFor(editType)}
                      placeholder={t('categoryRules.selectCategory')}
                    />
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-8 w-8 cursor-pointer"
                      onClick={submitEdit}
                      disabled={updateRule.isPending}
                    >
                      <Check className="w-4 h-4 text-emerald-400" />
                    </Button>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-8 w-8 cursor-pointer"
                      onClick={() => setEditingId(null)}
                    >
                      <X className="w-4 h-4" />
                    </Button>
                  </>
                ) : (
                  <>
                    <TypeBadge type={rule.txnType} />
                    <code className="text-sm font-mono bg-muted/40 px-1.5 py-0.5 rounded flex-1 truncate">
                      {rule.pattern}
                    </code>
                    <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      <span
                        className="w-2 h-2 rounded-full flex-shrink-0"
                        style={{ backgroundColor: rule.categoryColor ?? '#64748b' }}
                      />
                      <span className="truncate max-w-[120px]">{rule.categoryName}</span>
                    </span>
                    <span className="text-[11px] text-sky-400 bg-sky-500/10 border border-sky-500/20 font-mono tabular-nums px-1.5 py-0.5 rounded">
                      {t('categoryRules.matchCount', { count: rule.matchCount })}
                    </span>
                    <button
                      type="button"
                      onClick={() => startEdit(rule)}
                      className="p-1.5 rounded hover:bg-accent cursor-pointer text-muted-foreground hover:text-foreground transition-colors"
                      title={t('common.edit')}
                    >
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => confirmDelete(rule.id)}
                      className={cn(
                        'p-1.5 rounded cursor-pointer transition-colors',
                        pendingDelete === rule.id
                          ? 'bg-destructive/20 text-destructive'
                          : 'hover:bg-destructive/10 text-destructive/80 hover:text-destructive',
                      )}
                      title={pendingDelete === rule.id ? t('common.confirmDelete') : t('common.delete')}
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function TypePicker({ value, onChange }: { value: TxnType; onChange: (v: TxnType) => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-1 border border-input rounded-md p-0.5">
      {(['EXPENSE', 'INCOME'] as TxnType[]).map((code) => {
        const active = value === code;
        const Icon = code === 'EXPENSE' ? ArrowDownCircle : ArrowUpCircle;
        return (
          <button
            key={code}
            type="button"
            onClick={() => onChange(code)}
            className={cn(
              'flex items-center gap-1 h-7 px-2 rounded-[3px] text-xs font-medium transition-colors cursor-pointer',
              active
                ? code === 'EXPENSE'
                  ? 'bg-red-500/10 text-red-400'
                  : 'bg-emerald-500/10 text-emerald-400'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            <Icon className="w-3 h-3" />
            <span>{t(code === 'EXPENSE' ? 'categoryRules.expense' : 'categoryRules.income')}</span>
          </button>
        );
      })}
    </div>
  );
}

function TypeBadge({ type }: { type: TxnType }) {
  const { t } = useTranslation();
  const Icon = type === 'EXPENSE' ? ArrowDownCircle : ArrowUpCircle;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 text-[11px] font-medium px-1.5 py-0.5 rounded flex-shrink-0',
        type === 'EXPENSE'
          ? 'bg-red-500/10 text-red-400'
          : 'bg-emerald-500/10 text-emerald-400',
      )}
    >
      <Icon className="w-3 h-3" />
      {t(type === 'EXPENSE' ? 'categoryRules.expense' : 'categoryRules.income')}
    </span>
  );
}

function CategorySelect({
  value,
  onChange,
  options,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  options: Array<{ id: string; name: string; color: string }>;
  placeholder: string;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="h-8 text-sm bg-background border border-input rounded-md px-2 cursor-pointer hover:border-ring/60 focus:outline-none focus:ring-2 focus:ring-ring/40 transition min-w-[140px]"
    >
      <option value="" disabled>
        {placeholder}
      </option>
      {options.map((o) => (
        <option key={o.id} value={o.id}>
          {o.name}
        </option>
      ))}
    </select>
  );
}
