import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Trash2, Tag as TagIcon, FolderInput, X, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Tag } from '@/types/tag.types';
import type { Category } from '@/types/budget.types';

interface Props {
  count: number;
  onClear: () => void;
  onDelete: () => void;
  onAddTag: (tagId: string) => void;
  onSetCategory: (categoryId: string) => void;
  onClearCategory: () => void;
  tags: Tag[];
  incomeCategories: Category[];
  expenseCategories: Category[];
  pending: boolean;
}

export function BulkActionBar({
  count,
  onClear,
  onDelete,
  onAddTag,
  onSetCategory,
  onClearCategory,
  tags,
  incomeCategories,
  expenseCategories,
  pending,
}: Props) {
  const { t } = useTranslation();
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    if (!confirmDelete) return;
    const id = setTimeout(() => setConfirmDelete(false), 3000);
    return () => clearTimeout(id);
  }, [confirmDelete]);

  const handleDelete = () => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }
    onDelete();
    setConfirmDelete(false);
  };

  return (
    <div className="flex items-center gap-2 px-3 py-2 mx-6 my-2 rounded-md border border-sky-500/30 bg-sky-500/[0.06]">
      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-sky-300 font-mono tabular-nums">
        <span className="w-1.5 h-1.5 rounded-full bg-sky-400 animate-pulse" />
        {t('budget.bulk.selected', { count })}
      </span>
      <div className="ml-auto flex items-center gap-1.5">
        <TagPopover tags={tags} onPick={onAddTag} disabled={pending} />
        <CategoryPopover
          incomeCategories={incomeCategories}
          expenseCategories={expenseCategories}
          onPick={onSetCategory}
          onClear={onClearCategory}
          disabled={pending}
        />
        <Button
          variant="ghost"
          size="sm"
          onClick={handleDelete}
          disabled={pending}
          className={cn(
            'h-7 px-2 text-xs cursor-pointer',
            confirmDelete
              ? 'bg-destructive/20 text-destructive hover:bg-destructive/30'
              : 'text-destructive/80 hover:text-destructive hover:bg-destructive/10',
          )}
        >
          {pending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
          <span className="ml-1.5">
            {confirmDelete ? t('common.confirmDelete') : t('common.delete')}
          </span>
        </Button>
        <button
          type="button"
          onClick={onClear}
          className="p-1.5 rounded hover:bg-accent cursor-pointer text-muted-foreground hover:text-foreground transition-colors"
          title={t('budget.bulk.clear')}
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
}

function TagPopover({ tags, onPick, disabled }: { tags: Tag[]; onPick: (id: string) => void; disabled: boolean }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useClickOutside(ref, () => setOpen(false));

  return (
    <div className="relative" ref={ref}>
      <Button
        variant="ghost"
        size="sm"
        disabled={disabled || tags.length === 0}
        onClick={() => setOpen((v) => !v)}
        className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground cursor-pointer"
      >
        <TagIcon className="w-3.5 h-3.5" />
        <span className="ml-1.5">{t('budget.bulk.addTag')}</span>
      </Button>
      {open && (
        <div className="absolute right-0 top-full mt-1 z-30 min-w-[180px] max-h-[220px] overflow-auto rounded-md border border-border bg-popover shadow-lg p-1">
          {tags.map((tag) => (
            <button
              key={tag.id}
              type="button"
              onClick={() => {
                onPick(tag.id);
                setOpen(false);
              }}
              className="w-full flex items-center gap-2 px-2 py-1.5 rounded text-xs hover:bg-accent cursor-pointer text-left"
            >
              <span
                className="w-2 h-2 rounded-full flex-shrink-0"
                style={{ backgroundColor: tag.color ?? '#64748b' }}
              />
              <span className="truncate">{tag.name}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function CategoryPopover({
  incomeCategories,
  expenseCategories,
  onPick,
  onClear,
  disabled,
}: {
  incomeCategories: Category[];
  expenseCategories: Category[];
  onPick: (id: string) => void;
  onClear: () => void;
  disabled: boolean;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useClickOutside(ref, () => setOpen(false));

  const totalCats = incomeCategories.length + expenseCategories.length;

  return (
    <div className="relative" ref={ref}>
      <Button
        variant="ghost"
        size="sm"
        disabled={disabled || totalCats === 0}
        onClick={() => setOpen((v) => !v)}
        className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground cursor-pointer"
      >
        <FolderInput className="w-3.5 h-3.5" />
        <span className="ml-1.5">{t('budget.bulk.setCategory')}</span>
      </Button>
      {open && (
        <div className="absolute right-0 top-full mt-1 z-30 min-w-[220px] max-h-[280px] overflow-auto rounded-md border border-border bg-popover shadow-lg p-1">
          {expenseCategories.length > 0 && (
            <>
              <div className="px-2 py-1 text-[10px] uppercase tracking-wider text-red-400/80 font-semibold">
                {t('budget.expenses')}
              </div>
              {expenseCategories.map((cat) => (
                <CategoryRow key={cat.id} cat={cat} onClick={() => { onPick(cat.id); setOpen(false); }} />
              ))}
            </>
          )}
          {incomeCategories.length > 0 && (
            <>
              <div className="px-2 py-1 text-[10px] uppercase tracking-wider text-emerald-400/80 font-semibold">
                {t('budget.income')}
              </div>
              {incomeCategories.map((cat) => (
                <CategoryRow key={cat.id} cat={cat} onClick={() => { onPick(cat.id); setOpen(false); }} />
              ))}
            </>
          )}
          <div className="h-px bg-border my-1" />
          <button
            type="button"
            onClick={() => {
              onClear();
              setOpen(false);
            }}
            className="w-full flex items-center gap-2 px-2 py-1.5 rounded text-xs hover:bg-accent cursor-pointer text-muted-foreground"
          >
            <span className="w-2 h-2 rounded-full border border-muted-foreground flex-shrink-0" />
            <span>{t('budget.bulk.clearCategory')}</span>
          </button>
        </div>
      )}
    </div>
  );
}

function CategoryRow({ cat, onClick }: { cat: Category; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded text-xs hover:bg-accent cursor-pointer text-left"
    >
      <span
        className="w-2 h-2 rounded-full flex-shrink-0"
        style={{ backgroundColor: cat.color ?? '#64748b' }}
      />
      <span className="truncate">{cat.name}</span>
    </button>
  );
}

function useClickOutside(ref: React.RefObject<HTMLElement>, handler: () => void) {
  useEffect(() => {
    const listener = (e: MouseEvent) => {
      if (!ref.current || ref.current.contains(e.target as Node)) return;
      handler();
    };
    document.addEventListener('mousedown', listener);
    return () => document.removeEventListener('mousedown', listener);
  }, [ref, handler]);
}
