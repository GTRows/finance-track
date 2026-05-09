import { describe, expect, it, vi, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { VirtualizedList, VIRTUALIZATION_THRESHOLD } from './VirtualizedList';

interface Row {
  id: string;
  label: string;
}

function buildRows(n: number): Row[] {
  return Array.from({ length: n }, (_, i) => ({ id: `id-${i}`, label: `row-${i}` }));
}

describe('VirtualizedList', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders all rows when items.length is below the threshold', () => {
    const rows = buildRows(100);
    render(
      <VirtualizedList<Row>
        items={rows}
        getItemKey={(it) => it.id}
        estimateSize={32}
        renderRow={(it) => <span>{it.label}</span>}
      />,
    );

    expect(screen.getAllByRole('row').length).toBe(100);
  });

  it('renders all rows at exactly the threshold (1000)', () => {
    const rows = buildRows(VIRTUALIZATION_THRESHOLD);
    render(
      <VirtualizedList<Row>
        items={rows}
        getItemKey={(it) => it.id}
        estimateSize={32}
        renderRow={(it) => <span>{it.label}</span>}
      />,
    );

    expect(screen.getAllByRole('row').length).toBe(VIRTUALIZATION_THRESHOLD);
  });

  describe('above the threshold (virtualized)', () => {
    let originalGetBoundingClientRect: typeof Element.prototype.getBoundingClientRect;
    let originalResizeObserver: typeof globalThis.ResizeObserver | undefined;

    beforeAll(() => {
      originalGetBoundingClientRect = Element.prototype.getBoundingClientRect;
      Element.prototype.getBoundingClientRect = function (): DOMRect {
        return {
          height: 600,
          width: 800,
          top: 0,
          left: 0,
          bottom: 600,
          right: 800,
          x: 0,
          y: 0,
          toJSON: () => ({}),
        } as DOMRect;
      };
      originalResizeObserver = globalThis.ResizeObserver;
      class MockResizeObserver implements ResizeObserver {
        constructor(private cb: ResizeObserverCallback) {}
        observe(target: Element): void {
          const rect = target.getBoundingClientRect();
          const entry = {
            target,
            contentRect: rect,
            borderBoxSize: [{ inlineSize: rect.width, blockSize: rect.height }],
            contentBoxSize: [{ inlineSize: rect.width, blockSize: rect.height }],
            devicePixelContentBoxSize: [
              { inlineSize: rect.width, blockSize: rect.height },
            ],
          } as unknown as ResizeObserverEntry;
          this.cb([entry], this);
        }
        unobserve(): void {}
        disconnect(): void {}
      }
      globalThis.ResizeObserver = MockResizeObserver as unknown as typeof globalThis.ResizeObserver;
    });

    afterAll(() => {
      Element.prototype.getBoundingClientRect = originalGetBoundingClientRect;
      if (originalResizeObserver) {
        globalThis.ResizeObserver = originalResizeObserver;
      }
    });

    it('virtualizes when items.length exceeds the threshold', async () => {
      const rows = buildRows(VIRTUALIZATION_THRESHOLD + 1);
      render(
        <VirtualizedList<Row>
          items={rows}
          getItemKey={(it) => it.id}
          estimateSize={40}
          renderRow={(it) => <span>{it.label}</span>}
        />,
      );

      const mountedRows = await screen.findAllByRole('row');
      expect(mountedRows.length).toBeGreaterThan(0);
      expect(mountedRows.length).toBeLessThanOrEqual(50);
    });

    it('row click handler fires with the correct item identity', async () => {
      const onClick = vi.fn();
      const rows = buildRows(1500);
      render(
        <VirtualizedList<Row>
          items={rows}
          getItemKey={(it) => it.id}
          estimateSize={40}
          renderRow={(it) => (
            <button onClick={() => onClick(it.id)}>{it.label}</button>
          )}
        />,
      );

      const firstButton = await screen.findByText('row-0');
      fireEvent.click(firstButton);
      expect(onClick).toHaveBeenCalledWith('id-0');
    });
  });

  it('container carries role=rowgroup and rendered rows carry role=row', () => {
    const rows = buildRows(5);
    render(
      <VirtualizedList<Row>
        items={rows}
        getItemKey={(it) => it.id}
        estimateSize={32}
        ariaLabel="test-list"
        renderRow={(it) => <span>{it.label}</span>}
      />,
    );

    expect(screen.getAllByRole('rowgroup').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByRole('row').length).toBe(5);
  });
});
