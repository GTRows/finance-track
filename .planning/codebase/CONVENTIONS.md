# Coding Conventions

**Analysis Date:** 2026-05-03

## Naming Patterns

**Java / backend:**
- Packages: feature-based, lowercase — `com.fintrack.portfolio`, `com.fintrack.auth`
- Classes: PascalCase — `PortfolioController`, `JwtAuthFilter`, `PriceSyncService`
- Methods: camelCase, verb-first — `getForUser()`, `refreshLive()`, `evaluateAll()`
- Constants: SCREAMING_SNAKE_CASE
- DTOs: PascalCase records with `Request` or `Response` suffix — `CreateAlertRequest`, `AlertResponse`
- Repositories: `XRepository extends JpaRepository<X, UUID>`
- Services: `XService` (or `XOperationService` when split)
- Controllers: `XController`
- Custom exceptions: PascalCase + `Exception` — `ResourceNotFoundException`, `BusinessRuleException`
- Enum values: SCREAMING_SNAKE_CASE
- Test classes: `XTest` (unit), `XWebMvcTest` (controller slice)

**TypeScript / frontend:**
- Component files: PascalCase `.tsx` — `AddTransactionDialog.tsx`
- Component identifiers: PascalCase functions with `Props` interface — `interface AddTransactionDialogProps { ... }`
- Hooks: camelCase, `use` prefix — `useAssets`, `useLivePrices`
- API modules: `{feature}.api.ts` exporting an object literal — `authApi`, `billsApi`, `assetApi`
- Stores: `{feature}.store.ts`, hook export `useXStore` — `useAuthStore`
- Types: `{feature}.types.ts`, PascalCase types
- Constants: camelCase locals; SCREAMING_SNAKE_CASE for module-level
- Variables: camelCase

**Database:**
- Tables: `snake_case`, plural where it reads naturally (`users`, `portfolio_holdings`, `price_history`)
- Columns: `snake_case`; timestamps `created_at`, `updated_at`
- Primary keys: `id UUID`; foreign keys: `{entity}_id`
- Migrations: `V{n}__{description}.sql`

## Code Style

**Backend:**
- Formatter: Spotless Maven Plugin with Google Java Format (AOSP) — `backend/pom.xml`
- Indent: 2 spaces (Google style); semicolons required (Java)
- Removes unused imports automatically
- Run formatter: `./mvnw spotless:apply`; verify in `./mvnw verify`

**Frontend:**
- ESLint: `frontend/.eslintrc.cjs`
  - Extends `eslint:recommended` and `plugin:@typescript-eslint/recommended`
  - `@typescript-eslint/no-unused-vars`: error (allows `_` prefix)
  - `@typescript-eslint/no-floating-promises`: error
  - `@typescript-eslint/consistent-type-imports`: error
  - `react-hooks/rules-of-hooks`: error
  - `npm run lint` runs with `--max-warnings 0`
- TypeScript: `strict` mode enabled in `frontend/tsconfig.json`
- No explicit `.prettierrc` checked in; Prettier defaults assumed if used locally
- Run: `npm run lint`, `npm run typecheck`

## Import Organization

**Backend:** Standard alphabetical Java imports; Spotless removes unused entries.

**Frontend:**
- Path alias `@/*` → `./src/*` (`tsconfig.json`)
- `@typescript-eslint/consistent-type-imports` enforces `import type { … }` for type-only imports
- API calls go through `frontend/src/api/client.ts` (axios instance with auth interceptors)

## Error Handling

**Backend:**
- Services throw typed exceptions; `GlobalExceptionHandler` (`backend/src/main/java/com/fintrack/common/exception/GlobalExceptionHandler.java`) maps them to a uniform JSON shape
- Controllers always return `ResponseEntity<T>` with explicit status
- Repositories return `Optional<T>`; services unwrap via `orElseThrow(...)` with a domain exception
- Validation: Jakarta Bean Validation on DTOs (`@Valid`, `@NotBlank`, `@NotNull`, `@Email`, `@Min`, `@Max`)

**Frontend:**
- React Query handles async errors; components consume `error` state from hooks
- Axios interceptor in `frontend/src/api/client.ts` intercepts 401 once and calls `/auth/refresh`; on failure logs out and routes to `/login`
- Floating promises are forbidden — `await` or `void` them explicitly

## Logging

**Backend:**
- SLF4J via Lombok `@Slf4j`
- Structured placeholder format: `log.info("Alert created: id={} userId={} ...", id, userId, ...)`
- `RequestLoggingFilter` puts a request id in MDC; included in log lines

**Frontend:**
- No central logger; `console.*` is the fallback
- Avoid leaving `console.log` in committed code

## Comments

- Project rule: do not add comments to code you did not change (per global and project `CLAUDE.md`)
- Self-explanatory naming over commentary
- Method-level Javadoc allowed when documenting non-obvious invariants
- No filler/banner comments, no "why this method exists" docstrings on trivial code

## Function Design

**Backend:**
- Thin controllers; services own logic and transactions
- DTOs are records — immutable, validated at the boundary
- Use `@Transactional` on writes, `@Transactional(readOnly = true)` on reads
- Prefer `Optional` over `null` returns from repositories

**Frontend:**
- Functional components only; no class components
- Custom hooks wrap React Query calls; default `staleTime` ~5 min for list views
- API modules export objects with named methods (`authApi.login`, `billsApi.list`)
- Zustand stores expose state plus action setters; persist only when needed (`auth.store.ts` persists user + refresh token to localStorage under key `fintrack-auth`)

## Module Design

**Backend:**
- Feature packages are self-contained: controller, service(s), repository, DTOs in `dto/`
- Cross-cutting code lives in `common/` (entity, config, exception, filter, web)
- `@Component`, `@Service`, `@Repository`, `@RestController` drive Spring DI

**Frontend:**
- Named exports preferred for hooks, stores, API objects, and types
- Default exports reserved for React components when ergonomics call for it
- `frontend/src/components/ui/` re-exports shadcn/ui primitives — never reinvent buttons/dialogs/inputs

---

*Convention analysis: 2026-05-03*
*Update when patterns change*
