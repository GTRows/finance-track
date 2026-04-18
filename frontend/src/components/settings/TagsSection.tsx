import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tag as TagIcon, Trash2, Check, X, Pencil } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useTags, useCreateTag, useUpdateTag, useDeleteTag } from '@/hooks/useTags';
import type { Tag } from '@/types/tag.types';

const PALETTE = [
  '#ef4444', '#f97316', '#f59e0b', '#eab308',
  '#84cc16', '#22c55e', '#14b8a6', '#06b6d4',
  '#0ea5e9', '#6366f1', '#8b5cf6', '#ec4899',
];

export function TagsSection() {
  const { t } = useTranslation();
  const { data: tags = [] } = useTags();
  const createTag = useCreateTag();
  const updateTag = useUpdateTag();
  const deleteTag = useDeleteTag();

  const [newName, setNewName] = useState('');
  const [newColor, setNewColor] = useState(PALETTE[8]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editColor, setEditColor] = useState<string>(PALETTE[0]);
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);

  const handleCreate = async () => {
    const name = newName.trim();
    if (!name) return;
    try {
      await createTag.mutateAsync({ name, color: newColor });
      setNewName('');
    } catch {
      // duplicate — ignored
    }
  };

  const startEdit = (tag: Tag) => {
    setEditingId(tag.id);
    setEditName(tag.name);
    setEditColor(tag.color ?? PALETTE[0]);
  };

  const submitEdit = async () => {
    if (!editingId) return;
    const name = editName.trim();
    if (!name) return;
    try {
      await updateTag.mutateAsync({ id: editingId, req: { name, color: editColor } });
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
    await deleteTag.mutateAsync(id);
    setPendingDelete(null);
  };

  return (
    <div className="space-y-4">
      {/* Add new */}
      <div className="flex flex-wrap items-center gap-2">
        <Input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleCreate();
            }
          }}
          placeholder={t('tag.newTagPlaceholder')}
          className="h-9 w-[220px]"
        />
        <ColorPicker value={newColor} onChange={setNewColor} />
        <Button
          onClick={handleCreate}
          disabled={!newName.trim() || createTag.isPending}
          size="sm"
          className="cursor-pointer"
        >
          {t('common.add')}
        </Button>
      </div>

      {/* List */}
      {tags.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-8 text-center border border-dashed border-border rounded-md">
          <div className="w-10 h-10 rounded-lg bg-muted flex items-center justify-center mb-3">
            <TagIcon className="w-5 h-5 text-muted-foreground" />
          </div>
          <p className="text-sm text-muted-foreground">{t('tag.empty')}</p>
        </div>
      ) : (
        <ul className="divide-y divide-border border border-border rounded-md overflow-hidden">
          {tags.map((tag) => {
            const editing = editingId === tag.id;
            return (
              <li key={tag.id} className="flex items-center gap-3 px-3 py-2 hover:bg-accent/30 transition-colors">
                {editing ? (
                  <>
                    <ColorPicker value={editColor} onChange={setEditColor} />
                    <Input
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') submitEdit();
                        if (e.key === 'Escape') setEditingId(null);
                      }}
                      className="h-8 flex-1"
                    />
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-8 w-8 cursor-pointer"
                      onClick={submitEdit}
                      disabled={updateTag.isPending}
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
                    <span
                      className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                      style={{ backgroundColor: tag.color ?? '#64748b' }}
                    />
                    <span className="text-sm flex-1 truncate">{tag.name}</span>
                    <span className="text-[11px] text-muted-foreground font-mono tabular-nums">
                      {t('tag.usageCount', { count: tag.usageCount })}
                    </span>
                    <button
                      type="button"
                      onClick={() => startEdit(tag)}
                      className="p-1.5 rounded hover:bg-accent cursor-pointer text-muted-foreground hover:text-foreground transition-colors"
                      title={t('common.edit')}
                    >
                      <Pencil className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => confirmDelete(tag.id)}
                      className={cn(
                        'p-1.5 rounded cursor-pointer transition-colors',
                        pendingDelete === tag.id
                          ? 'bg-destructive/20 text-destructive'
                          : 'hover:bg-destructive/10 text-destructive/80 hover:text-destructive'
                      )}
                      title={pendingDelete === tag.id ? t('common.confirmDelete') : t('common.delete')}
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

function ColorPicker({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="h-8 w-8 rounded-md border border-input flex items-center justify-center cursor-pointer hover:ring-2 hover:ring-ring/40 transition"
        title="color"
      >
        <span className="w-4 h-4 rounded-sm" style={{ backgroundColor: value }} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute z-20 mt-1 grid grid-cols-6 gap-1 p-2 rounded-md border border-border bg-popover shadow-lg">
            {PALETTE.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => { onChange(c); setOpen(false); }}
                className={cn(
                  'w-6 h-6 rounded-sm cursor-pointer hover:scale-110 transition',
                  value === c && 'ring-2 ring-ring'
                )}
                style={{ backgroundColor: c }}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
