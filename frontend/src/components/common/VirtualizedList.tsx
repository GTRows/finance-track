import { useRef, type ReactNode } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { cn } from '@/lib/utils';

/**
 * Threshold above which `VirtualizedList` switches from a plain map render
 * to a windowed render via `@tanstack/react-virtual`.
 *
 * Below or equal to this count the small-list code path is byte-for-byte
 * equivalent to `items.map(renderRow)` so existing layout, hover states,
 * and DOM structure are preserved.
 */
export const VIRTUALIZATION_THRESHOLD = 1000;

/**
 * Generic typed prop surface for the virtualized list primitive.
 *
 * Ownership boundary: this primitive owns the windowing logic ONLY. Row
 * markup stays at the call site (the `renderRow` callback). The primitive
 * wraps each rendered row in `<div role="row">` and the call site's
 * returned ReactNode is its only child.
 *
 * ARIA contract: the body container carries `role="rowgroup"` and each
 * row carries `role="row"`. Cells inside the row are the call site's
 * responsibility (call site emits `<div role="cell">` or equivalent).
 */
interface VirtualizedListProps<TItem> {
  /** The items to render. */
  items: TItem[];
  /** Stable key extractor — used by both the small-list and virtualized paths. */
  getItemKey: (item: TItem) => string;
  /** Estimated row height in pixels. Used by the virtualizer to size the scroll container. */
  estimateSize: number;
  /** Number of rows to render above and below the visible window. Default 10. */
  overscan?: number;
  /** Override the default `VIRTUALIZATION_THRESHOLD` — primarily for tests. */
  threshold?: number;
  /** Render the row for a given item. Call site owns markup + classes. */
  renderRow: (item: TItem, index: number) => ReactNode;
  /** Optional sticky header rendered above the row body. Not virtualized. */
  renderHeader?: () => ReactNode;
  /** Optional Tailwind/utility classes applied to the outer container. */
  className?: string;
  /** Optional empty state rendered when `items.length === 0`. */
  emptyState?: ReactNode;
  /** Optional `aria-label` applied to the rowgroup container. */
  ariaLabel?: string;
}

export function VirtualizedList<TItem>({
  items,
  getItemKey,
  estimateSize,
  overscan,
  threshold,
  renderRow,
  renderHeader,
  className,
  emptyState,
  ariaLabel,
}: VirtualizedListProps<TItem>): JSX.Element {
  const containerRef = useRef<HTMLDivElement>(null);
  const limit = threshold ?? VIRTUALIZATION_THRESHOLD;
  const shouldVirtualize = items.length > limit;

  // useVirtualizer must be called unconditionally to satisfy the rules of hooks.
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => containerRef.current,
    estimateSize: () => estimateSize,
    overscan: overscan ?? 10,
    getItemKey: (index) => getItemKey(items[index]),
  });

  if (items.length === 0 && emptyState !== undefined) {
    return <>{emptyState}</>;
  }

  if (!shouldVirtualize) {
    return (
      <div className={className}>
        {renderHeader?.()}
        <div role="rowgroup" aria-label={ariaLabel}>
          {items.map((item, index) => (
            <div role="row" key={getItemKey(item)}>
              {renderRow(item, index)}
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className={cn('overflow-auto', className)}
      style={{ height: '70vh' }}
    >
      {renderHeader?.()}
      <div
        role="rowgroup"
        aria-label={ariaLabel}
        style={{ height: `${virtualizer.getTotalSize()}px`, position: 'relative' }}
      >
        {virtualizer.getVirtualItems().map((virtualRow) => (
          <div
            role="row"
            key={virtualRow.key}
            data-index={virtualRow.index}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${virtualRow.start}px)`,
            }}
          >
            {renderRow(items[virtualRow.index], virtualRow.index)}
          </div>
        ))}
      </div>
    </div>
  );
}
