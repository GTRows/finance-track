# Testing Patterns

**Analysis Date:** 2026-05-03

## Test Framework

**Backend:**
- Runner: JUnit 5 (Jupiter)
- Mocking: Mockito 5 (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`)
- Assertions: AssertJ (fluent)
- Spring slices: `spring-boot-starter-test`, `spring-security-test`
- Integration: Testcontainers (PostgreSQL) — declared in `backend/pom.xml`
- Coverage: JaCoCo — minimum 60% instructions, 45% branches; gated in `verify`
  - Excludes: `FinTrackApplication`, `common/entity/**`, `**/dto/**`, `**/*Response*`, `**/*Request*`

**Frontend:**
- Runner: Vitest 1.3 with `jsdom` environment (`frontend/vite.config.ts`)
- Mocking: `vi.mock`, `vi.spyOn`, `vi.fn`, `vi.mocked`
- DOM: `@testing-library/react`, `@testing-library/user-event`
- Coverage: not enforced; available via `vitest --coverage`

## Run Commands

**Backend:**
```
cd backend && ./mvnw test                  # unit + slice tests
cd backend && ./mvnw verify                # tests + JaCoCo gate
cd backend && ./mvnw test jacoco:report    # HTML report at target/site/jacoco/index.html
```

**Frontend:**
```
cd frontend && npm run test       # vitest run
cd frontend && npm run typecheck  # tsc --noEmit
cd frontend && npm run lint       # eslint --max-warnings 0
cd frontend && npx vitest         # watch mode
```

## Test File Organization

**Backend:**
- Location: mirrors source under `backend/src/test/java/com/fintrack/{feature}/`
- Naming: `{Class}Test.java` for unit, `{Class}WebMvcTest.java` for controller slices
- Examples: `PriceAlertServiceTest`, `AdminControllerWebMvcTest`, `PriceAlertControllerWebMvcTest`

**Frontend:**
- Location: collocated next to source
- Naming: `{name}.test.ts` or `{name}.test.tsx`
- Examples: `frontend/src/api/bills.api.test.ts`, `frontend/src/hooks/useAssets.test.tsx`, `frontend/src/components/budget/AddTransactionDialog.test.tsx`

## Test Structure

**Backend pattern (Mockito):**
```java
@ExtendWith(MockitoExtension.class)
class PriceAlertServiceTest {
  @Mock PriceAlertRepository alertRepo;
  @Mock AssetRepository assetRepo;
  @InjectMocks PriceAlertService service;

  @Test
  void create_persistsAlertWithRequestedThreshold() {
    Asset btc = asset("BTC", "100");
    when(assetRepo.findById(btc.getId())).thenReturn(Optional.of(btc));

    AlertResponse res = service.create(userId, request);

    assertThat(res.assetSymbol()).isEqualTo("BTC");
    verify(alertRepo).save(any(PriceAlert.class));
  }
}
```
- Helper factory methods (`asset(...)`, `alert(...)`) build entities with builders
- `ArgumentCaptor` used to assert persisted state

**Frontend pattern (API module):**
```ts
describe('billsApi', () => {
  afterEach(() => vi.restoreAllMocks());

  it('creates a bill via POST /bills', async () => {
    const body = { name: 'Rent', amount: 1000, dueDay: 1 };
    const spy = vi.spyOn(client, 'post').mockResolvedValue({ data: body });

    await billsApi.create(body);

    expect(spy).toHaveBeenCalledWith('/bills', body);
  });
});
```

**Frontend pattern (component, RTL + QueryClient wrapper):**
```ts
function renderDialog(onSubmit = vi.fn()) {
  const { Wrapper } = createWrapper();
  vi.mocked(tagsApi.list).mockResolvedValue([]);
  return {
    ...render(
      <AddTransactionDialog
        incomeCategories={incomeCats}
        expenseCategories={expenseCats}
        onSubmit={onSubmit}
        isPending={false}
      />,
      { wrapper: Wrapper },
    ),
    onSubmit,
  };
}
```

**Frontend pattern (hook):**
```ts
vi.mock('@/api/asset.api', () => ({ assetApi: { list: vi.fn() } }));

it('passes the type filter through', async () => {
  vi.mocked(assetApi.list).mockResolvedValueOnce([]);
  const { Wrapper } = createWrapper();

  renderHook(() => useAssets('CRYPTO'), { wrapper: Wrapper });
  await waitFor(() => expect(assetApi.list).toHaveBeenCalledWith('CRYPTO'));
});
```

## Mocking

**Backend:** Mockito; mock collaborators, inject the unit under test. `verify(...)` for interactions, `ArgumentCaptor` for stored payloads, `verify(mock, never()).method(...)` for absence-of-call assertions.

**Frontend:**
- `vi.mock('@/api/...')` at module top to swap entire modules
- `vi.spyOn(client, 'post')` to assert HTTP calls in API module tests
- `vi.restoreAllMocks()` in `afterEach` keeps tests isolated
- Mock external boundaries (axios client, browser APIs); leave pure utilities real

## Fixtures and Factories

**Backend:** Private factory methods in test classes (`asset(...)`, `alert(...)`) return entities built with the project's `@Builder`. Random UUIDs via `UUID.randomUUID()`.

**Frontend:** Test setup helpers under `frontend/src/test-utils/` (e.g., `createWrapper()` providing a `QueryClientProvider`). Inline object literals for small test data. No global fixture registry.

## Coverage

**Backend (enforced):**
- Instruction coverage: 60% minimum
- Branch coverage: 45% minimum
- Excludes: app main class, entities, DTOs, request/response records
- Report: `backend/target/site/jacoco/index.html`

**Frontend:** No threshold enforced. Coverage report can be generated with `vitest --coverage` when needed.

## Test Types Present

**Backend:**
- Unit tests for services with mocked collaborators
- WebMvc slice tests for controllers (`@WebMvcTest`)
- Testcontainers wiring is configured but integration coverage is uneven

**Frontend:**
- API module tests (axios client mocked)
- Component tests with React Testing Library
- Hook tests via `renderHook`
- No end-to-end suite committed (Playwright not configured)

## Common Patterns

- Backend: `@Transactional` is implicit through Spring; tests do not manage transactions manually
- Backend: services that throw on missing entities are covered with both happy and failure paths
- Frontend: `await waitFor(() => …)` for React Query state transitions
- Frontend: prefer queries by role/text/placeholder (`screen.getByRole`, `getByPlaceholderText`)
- Frontend: keep mocks per-test with `mockResolvedValueOnce`; reset after each test

---

*Testing analysis: 2026-05-03*
*Update when test patterns or coverage thresholds change*
