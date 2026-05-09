/**
 * WebSocket price-batch envelope helpers.
 *
 * The backend's `/topic/prices` broadcaster emits a {@link LivePricesBatch} on every price-sync
 * tick. The first tick after a JVM start carries `deltaOnly === false` plus the full priced
 * universe so newly-connected clients get a snapshot. Subsequent ticks carry `deltaOnly === true`
 * and contain only the assets whose price moved by more than 0.01% relative to the prior tick.
 * Ticks with no material change emit no frame.
 *
 * `mergePriceBatch(prev, batch)` reduces both flavours to a single by-symbol map so callers do not
 * have to branch. Keyed by `symbol` to match the existing live-price store shape.
 */
export type LivePriceRow = {
  symbol: string;
  assetType: string;
  price: number;
  priceUsd: number | null;
  updatedAt: string;
};

export type LivePricesBatch = {
  publishedAt: string;
  count: number;
  totalAssets: number;
  deltaOnly: boolean;
  prices: LivePriceRow[];
};

/**
 * Merges a {@link LivePricesBatch} into the prior price map.
 *
 * - When `batch.deltaOnly === false` (cold-boot full broadcast), returns a fresh map built only
 *   from `batch.prices`. Stale prior entries are dropped.
 * - When `batch.deltaOnly === true` (steady-state delta), spreads the prior map and overlays each
 *   incoming row. Symbols not mentioned in `batch.prices` keep their prior value.
 */
export function mergePriceBatch(
  prev: Record<string, LivePriceRow>,
  batch: LivePricesBatch,
): Record<string, LivePriceRow> {
  if (!batch.deltaOnly) {
    const next: Record<string, LivePriceRow> = {};
    for (const row of batch.prices) {
      next[row.symbol] = row;
    }
    return next;
  }
  const next: Record<string, LivePriceRow> = { ...prev };
  for (const row of batch.prices) {
    next[row.symbol] = row;
  }
  return next;
}
