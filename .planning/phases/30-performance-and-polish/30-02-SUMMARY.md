---
phase: 30-performance-and-polish
plan: 02
subsystem: performance
tags: [postgres-indexes, pg-stat-statements, fk-coverage, monitoring-overlay, observed]

requires:
  - phase: 30
    plan: 01
    provides: AbstractDataJpaTestSupport reuse + QueryCountRegressionTest byte-for-byte template for the new IndexCoverageRegressionTest
  - phase: 28
    plan: 02
    provides: V46 migration shape pinning the next-forward V47 numbering
  - phase: 26
    plan: 03
    provides: monitoring/<name>/docker-compose.<name>.yml overlay precedent (prometheus + glitchtip) for the new monitoring/postgres/* opt-in profile
  - phase: 23
    plan: 01
    provides: AbstractDataJpaTestSupport Testcontainers harness + @EnabledIf("dockerAvailable") gate for the new test class

provides:
  - V47 Flyway migration adding 5 FK-coverage indexes (idx_holdings_asset_id, idx_price_alerts_asset_id, idx_alert_notifications_alert_id, idx_alert_notifications_asset_id partial, idx_savings_goals_linked_portfolio_id partial)
  - monitoring/postgres/postgresql.conf opt-in pg_stat_statements config
  - monitoring/postgres/docker-compose.postgres.yml operator-only compose overlay
  - monitoring/postgres/queries.sql operator helper queries (top-N + EXPLAIN template + reset)
  - IndexCoverageRegressionTest pinning the FK-leading-column coverage contract on pg_indexes + information_schema.referential_constraints
  - docs/OPERATIONS.md "Inspecting slow queries with pg_stat_statements" H2 section

affects: []

tech-stack:
  added: []
  patterns:
    - "Operator-only monitoring overlay: monitoring/<name>/docker-compose.<name>.yml ships the additions/overrides for the named service AND an :ro mount of any dedicated config file. The live docker-compose.yml stays clean (locked by pre_guard_release_files.py PreToolUse hook). Operator opts in via `docker compose -f docker-compose.yml -f monitoring/<name>/docker-compose.<name>.yml up -d <service>`. Pattern shared with 26-02 (glitchtip) and 26-03 (prometheus)."
    - "FK leading-column index coverage check: query `pg_index.indkey[0]` -> `pg_attribute.attname` to extract the leading column from EVERY index on a table (including composites and partials), then assert each `information_schema.referential_constraints`-derived FK column is the leading column of at least one such index. Free-text matching on `pg_indexes.indexdef` is rejected because composite-index leading-column extraction is what we actually want."
    - "Migration-time FK index audit: SQL-only Flyway migration with a top-of-file comment block summarising the inspection scope, plus per-statement comment trio (motivation / selectivity / write-cost) on every CREATE INDEX. Partial indexes (`WHERE col IS NOT NULL`) on nullable FK columns whose NULL-row ratio is high. CREATE INDEX (not CONCURRENTLY) is safe at single-user homelab scale because target tables are < ~10k rows."

key-files:
  added:
    - backend/src/main/resources/db/migration/V47__index_audit_phase30.sql
    - backend/src/test/java/com/fintrack/perf/IndexCoverageRegressionTest.java
    - monitoring/postgres/postgresql.conf
    - monitoring/postgres/docker-compose.postgres.yml
    - monitoring/postgres/queries.sql
    - .planning/phases/30-performance-and-polish/30-02-SUMMARY.md
  modified:
    - docs/OPERATIONS.md
    - .planning/STATE.md
    - .planning/ROADMAP.md
---

## Goal

Ship Track F2 as the second plan of Phase 30 "Performance & Polish". Three coupled concerns:

1. **Static query-plan inspection (proxy for live `EXPLAIN ANALYZE`).** A real `pg_stat_statements`-driven walk against a populated production-shaped database is not reachable from a planning step; the plan adopted the proxy approach: walk every `V1..V46` Flyway migration, enumerate the full FK column inventory, cross-reference each FK column against the existing index inventory and the repository methods, and add indexes for the obvious gaps.
2. **Missing-index Flyway migration `V47__index_audit_phase30.sql`.** Postgres does NOT auto-index foreign-key columns; the missing-FK-index batch tightens both `ON DELETE` reverse-scan cost on parent deletes AND the residual single-FK-column lookups in `findByAssetId` / asset-anchored reverse scans the 30-01 batched-repo refactor did NOT cover.
3. **Operator-only `pg_stat_statements` enablement.** Mirroring the 26-02 / 26-03 / 28-* monitoring-overlay precedent: a NEW compose overlay + dedicated `postgresql.conf` + helper SQL ship under `monitoring/postgres/`. The live `docker-compose.yml` is untouched (locked by the `pre_guard_release_files.py` PreToolUse hook).

A new `IndexCoverageRegressionTest extends AbstractDataJpaTestSupport` queries `pg_indexes` + `information_schema.referential_constraints` after Flyway runs and asserts every FK column declared in the schema is covered by at least one index whose leading-column list starts with that FK column. The test is `@EnabledIf("dockerAvailable")` to skip on CI hosts without Docker.

## What landed

**Backend:**

- New Flyway migration `V47__index_audit_phase30.sql` (5 indexes) with a top-of-file comment block + per-index 3-line comment trio (motivation / selectivity / write-cost):
  - `idx_holdings_asset_id` on `portfolio_holdings(asset_id)` — covers `HoldingRepository.findByPortfolioIdAndAssetId`'s second predicate; the `UNIQUE(portfolio_id, asset_id)` composite leads with `portfolio_id` so `asset_id`-only equality lookups fell back to a sequential scan.
  - `idx_price_alerts_asset_id` on `price_alerts(asset_id)` — `ON DELETE CASCADE` reverse scan when an `assets` row is removed; V9 indexes `(user_id)` + `(status)` only.
  - `idx_alert_notifications_alert_id` on `alert_notifications(alert_id)` — `ON DELETE CASCADE` reverse scan when a `price_alerts` row is removed.
  - `idx_alert_notifications_asset_id` on `alert_notifications(asset_id) WHERE asset_id IS NOT NULL` partial — `ON DELETE CASCADE` reverse scan when an `assets` row is removed; V10 dropped the NOT NULL on `asset_id` so the partial keeps the index byte-tight.
  - `idx_savings_goals_linked_portfolio_id` on `savings_goals(linked_portfolio_id) WHERE linked_portfolio_id IS NOT NULL` partial — `ON DELETE SET NULL` reverse scan when a `portfolios` row is removed; column is nullable + most goals are unlinked.
- New `IndexCoverageRegressionTest extends AbstractDataJpaTestSupport`. `@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")`; queries via `entityManager.createNativeQuery(...)`. Three test methods:
  - `everyForeignKeyColumnIsCoveredByALeadingColumnIndex` — joins `information_schema.referential_constraints` to `pg_index.indkey[0]` -> `pg_attribute.attname` per table, builds the UNCOVERED set, asserts empty with a failure message naming the offending `(table, column)` pairs.
  - `v47IndexesArePresentByName` — queries `pg_indexes` for the public schema and asserts the five V47 names are present (sentinel against a future drive-by `DROP INDEX`).
  - `noDuplicateIndexCoversTheSameLeadingColumnTuple` — groups `pg_index` by `(indrelid, indkey[0])` and asserts at most one non-unique non-PK index per tuple (sentinel against accidental redundancy).

**Monitoring overlay:**

- `monitoring/postgres/postgresql.conf` — `shared_preload_libraries = 'pg_stat_statements'` plus `pg_stat_statements.max = 10000` / `pg_stat_statements.track = all` / `pg_stat_statements.save = on`.
- `monitoring/postgres/docker-compose.postgres.yml` — operator-only overlay that overrides the postgres service's command to `postgres -c config_file=/etc/postgresql/postgresql.conf` and repeats the live compose's `postgres-data` volume + an `:ro` mount of the new conf at `/etc/postgresql/postgresql.conf`. Leading comment block documents the operator invocation.
- `monitoring/postgres/queries.sql` — three operator helpers: top-20 by total exec time, EXPLAIN template, `pg_stat_statements_reset()` reset snippet.

**Documentation:**

- `docs/OPERATIONS.md` gains a new `## Inspecting slow queries with pg_stat_statements` H2 covering: the opt-in overlay invocation, the one-time `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;` step, the canonical top-N query (cite `monitoring/postgres/queries.sql`), the `EXPLAIN (ANALYZE, BUFFERS) <sql>` workflow, the `SELECT pg_stat_statements_reset();` reset snippet, the hard rule that the live `docker-compose.yml` is never edited to enable the extension, and a forward note that future EXPLAIN walks revealing a NEW slow query path should result in a NEW `V{n}__index_audit_*.sql` migration NOT a runtime `ALTER`.

## Decisions Made

- **Five indexes, not the seed list's nine.** `recurring_templates.category_id`, `allocation_buckets.category_id`, and `transaction_category_rules.category_id` are all SOFT FKs (no `REFERENCES` declaration) so there is no parent-delete reverse scan to chase, and no repository method filters by `category_id` alone; `bills.due_day` ORDER BY at single-user scale is < 30 rows; `assets.asset_type` filter is < 200 rows; `authenticators.credential_id` is `BYTEA UNIQUE` (NOT a foreign key — the unique constraint already produces a covering index). All five SKIPs are documented in the V47 SQL comments + the transient `30-02-INSPECT.md` walk so the next reader does not re-add them.
- **`CREATE INDEX` (not `CONCURRENTLY`).** Flyway's transactional migrations cannot use `CONCURRENTLY` and at single-user homelab scale every target table is < ~10k rows so the table lock is sub-second. If the index pass were ever to be re-run on a multi-tenant deployment, switching to `CONCURRENTLY` would require splitting V47 into one index per migration file and removing the implicit Flyway transaction; future scaling concern, not a v1 issue.
- **Operator-only `pg_stat_statements`.** The extension is opt-in via the monitoring overlay; the live database does NOT load it. Mirrors the 26-02 / 26-03 monitoring-overlay precedent — the live `docker-compose.yml` is locked by the `pre_guard_release_files.py` PreToolUse hook and stays byte-for-byte unchanged.
- **`pg_index` leading-column extraction over `pg_indexes` indexdef parsing.** The test joins `pg_index.indkey[0]` -> `pg_attribute.attname` to extract the leading column from EVERY index (including composites and partials). Free-text matching `pg_indexes.indexdef` was rejected because composite-index leading-column extraction is what we actually want and the parser is two SQL columns and a join.
- **No application-side query rewrites.** 30-01 already refactored the N+1 hot paths; 30-02 only addresses the index inventory. Any query that EXPLAIN reveals as costly is documented in this SUMMARY for a future plan to handle, not silently rewritten here.
- **No dropping of existing indexes.** The audit only ADDS coverage; consolidation is a future plan.
- **`pg_stat_statements`-decoupled assertions.** `IndexCoverageRegressionTest` does NOT depend on `pg_stat_statements` being loaded (it only queries `pg_indexes` + `information_schema` + `pg_index` + `pg_attribute`). The overlay is independent and operator-only.

## Test Counts

- Backend (when Docker is available): `IndexCoverageRegressionTest` +3 cases (FK leading-column coverage / V47 names present / no-duplicate-leading-column-tuple). Test count delta target was +3 minimum; met. Locally Docker is not available so the slice is auto-skipped via `@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")`; CI exercises it.
- Frontend: unchanged (no frontend work in this plan).

## Verification Output

- `cd backend && ./mvnw.cmd verify` -> BUILD SUCCESS. JaCoCo 60% / 45% gate met (no production code change). Spotless clean.
- `cd backend && ./mvnw.cmd spotless:check` -> clean.
- `Grep("CREATE INDEX", "backend/src/main/resources/db/migration/V47__index_audit_phase30.sql")` -> 5 matches (matches the Task 1 final list size).
- `Grep("pg_stat_statements", "docs/OPERATIONS.md")` -> >= 3 matches.
- `Grep("Inspecting slow queries", "docs/OPERATIONS.md")` -> 1 match.
- `git diff docker-compose.yml` -> no diff (live compose is byte-for-byte unchanged).
- `git diff backend/pom.xml package.json package-lock.json .env.example CHANGELOG.md` -> no diff (deny-listed files untouched).
- `monitoring/postgres/{postgresql.conf,docker-compose.postgres.yml,queries.sql}` all exist.
- `.planning/phases/30-performance-and-polish/30-02-INSPECT.md` removed (transient discovery file).

## Deviations from Plan

- The seed list under `<objective>` named ~9 candidate indexes plus several skip cases; Task 1 inspection collapsed the final V47 list to exactly 5 (the 4 documented skips were reaffirmed as soft-FK-no-rev-scan, small-table sequential, BYTEA-UNIQUE-not-FK). The plan's "≤ 10 indexes" cap is comfortably met.
- OpenAPI regen continues to defer per the pre-existing 26-01 OpenTelemetry `ComponentLoader` `NoClassDefFoundError`. No new endpoint surface in this plan; nothing to regen.
- Local execution skips the Docker-gated `IndexCoverageRegressionTest` and `FlywayMigrationTest` slices via `@EnabledIf("dockerAvailable")`; CI exercises both. The full `mvnw.cmd verify` passes locally.

## Deferred Enhancements

- **Index consolidation pass.** The audit only ADDS coverage. A future "drop redundant indexes" plan would inspect cases like `idx_bills_user` against the actual hot read paths and consolidate where two indexes share a leading column. Out of scope here.
- **Pagination on `findAll`-style reads.** CONCERNS.md "Unbounded `findAll` in repositories" is partially addressed: V47 tightens the scan cost but does NOT add LIMIT / OFFSET to the audit-log / alert / asset listing endpoints. A dedicated future plan would split the listing surface into paginated endpoints + frontend pagination controls.
- **Live `pg_stat_statements` profile run + index-driven plan rewrite.** The static inspection here is a proxy. Once the operator runs the overlay against the production database and surfaces a slow query path V47 does NOT cover, that finding belongs in a NEW `V{n}__index_audit_*.sql` migration, NOT a runtime `ALTER` (per the OPERATIONS.md cross-reference).
- **`CONCURRENTLY` migration shape.** If the deployment ever scales beyond single-user homelab, switching V-numbered migrations to `CREATE INDEX CONCURRENTLY` would require one index per migration file (Flyway transactional wrapper conflicts with `CONCURRENTLY`). Future scaling concern.

## Rollback

`git revert` of this plan's commits is sufficient. The V47 indexes can be dropped via a forward `V48__rollback_30_02_indexes.sql` migration if ever needed (Flyway Community has no undo). The monitoring overlay revert is a file delete; nothing in the live compose references it. `IndexCoverageRegressionTest` revert restores the prior coverage assumption (no FK-coverage CI gate). No schema-shape change beyond the new indexes; Hibernate `ddl-auto=validate` stays green.

## Next Phase Readiness

Phase 30 sub-plan 03 (virtualized transaction list with `@tanstack/react-virtual`) is unblocked. The `IndexCoverageRegressionTest` slice + the V47 migration's per-index comment shape are ready to extend with new indexes as future EXPLAIN walks surface them. The operator runbook in `docs/OPERATIONS.md` is in place so the first real `pg_stat_statements` walk can happen at any time.
