# 30-03 Inspection Notes (transient — deleted by Task 8)

## Task 1 findings

### `BudgetPage.tsx` inline row block (lines 357-450)

Closure captured by the per-row markup:
- `selected: Set<string>` (line 71) — `selected.has(txn.id)` -> `isSelected`
- `selected.size > 0` -> `anySelected`
- `month: string` (line 57) — passed to `<ReceiptAction>`
- `locale` (line 58) — used in `new Date(...).toLocaleDateString(locale, {...})`
- `t()` from `useTranslation()` — title/aria copy on the checkbox + delete button
- `toggleSelect: (id: string) => void` (line 75)
- `deleteTxn.mutate: (id: string) => void` (line 67) -> wrapped in `onDelete`

The category dot uses `txn.categoryColor`. The amount uses `formatTRY(txn.amount)`. The receipt cell renders `<ReceiptAction>` with five props. Delete button reveals on hover via `opacity-0 group-hover:opacity-100`.

Resulting `TransactionRow` prop surface:
- `txn: BudgetTransaction`
- `selected: boolean`
- `anySelected: boolean`
- `month: string`
- `locale: string`
- `onToggleSelect: (id: string) => void`
- `onDelete: (id: string) => void`

Note: `incomeCategories` / `expenseCategories` from the plan's task line are NOT actually closed over by the row markup; the row only renders pre-resolved `txn.categoryName` + `txn.categoryColor`. Skipped from `TransactionRow` props per minimum-blast-radius rule.

### `TransactionLog.tsx` table contract (lines 86-154)

Eight columns:
1. `colDate` — left, muted, whitespace-nowrap
2. `colType` — left, type-badge with `TYPE_TONE[txn.txnType]`
3. `colAsset` — left, two-line stack: `assetSymbol` + `assetName`
4. `colQty` — right, `font-mono tabular-nums`
5. `colPrice` — right, `font-mono tabular-nums text-muted-foreground`
6. `colAmount` — right, `font-mono tabular-nums`
7. `colFee` — right, `font-mono tabular-nums text-muted-foreground`, `'--'` when zero
8. (no header text) — right, delete button

Grid template (per the plan):
```
grid-cols-[minmax(0,11ch)_minmax(0,8ch)_minmax(0,1fr)_minmax(0,12ch)_minmax(0,12ch)_minmax(0,12ch)_minmax(0,10ch)_minmax(0,4ch)]
```

Hover: `hover:bg-accent/30 transition-colors`. Cell padding: `px-4 py-2.5`. Border between rows: `border-b last:border-b-0`.

### Existing test queries

- `BudgetPage.test.tsx`: only asserts `screen.getAllByText(/budget/i).length > 0`. No `<table>` / `<tr>` / `<th>` literal queries. Does NOT touch `divide-y divide-border` literally. Stays untouched.
- `PortfolioDetailPage.test.tsx`: only asserts `await screen.findByText('Main')`. No table-tag queries. Stays untouched.

### Dependency state

- `Grep("@tanstack/react-virtual", "frontend/package.json")` -> 0 matches. Clean install path.
- `Grep("react-virtual|useVirtualizer", "frontend/src")` -> 0 matches. No half-shipped previous attempt.

## Plan ready to proceed

Task 2: add `@tanstack/react-virtual ^3.13.12` via Python staging-file.
Task 3: `npm install` to regenerate the lockfile.
Task 4-7: implement primitive + extracted row + two consumer migrations + tests.
Task 8: docs + STATE + ROADMAP + SUMMARY + delete this file.
