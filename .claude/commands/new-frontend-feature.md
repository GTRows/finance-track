---
description: Add a new frontend feature with page, components, hooks, and API module
---

Create a complete frontend feature for: $ARGUMENTS

Steps:
1. Read `docs/FRONTEND.md` for component patterns and design system
2. Read `docs/API.md` for the relevant endpoint contracts
3. Check `src/types/` — add TypeScript interfaces matching the API DTOs if missing
4. Create `src/api/{feature}.api.ts` — axios calls for this feature
5. Create `src/hooks/use{Feature}.ts` — React Query hooks wrapping the API calls
6. Create `src/components/{feature}/` directory with components
7. Create or update `src/pages/{Feature}Page.tsx`
8. Add route to `src/App.tsx` router if new page
9. All currency amounts: use `formatTRY()` from utils/formatters.ts
10. All components: use shadcn/ui primitives, never raw HTML inputs
11. Loading states: use skeleton loaders, not spinners
12. Error states: show friendly Turkish error messages
13. Update `tasks/TODO.md` — mark relevant items as done
