# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-04)

**Core value:** Owner can stop using the spreadsheet and trust this app for live portfolio P&L, monthly cash flow, and bill tracking — fully self-hosted.
**Current focus:** Phase 23 — Coverage Completion

## Current Position

Phase: 1 of 8 (Phase 23 — Coverage Completion)
Plan: 2 of 4 in current phase
Status: In progress
Last activity: 2026-05-04 — Completed 23-02-PLAN.md (A7 — pitest mutation gate at 60% project-level)

Progress: █░░░░░░░░░ 7%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: —
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| — | — | — | — |

**Recent Trend:**
- Last 5 plans: —
- Trend: —

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- 2026-05-04 (plan 23-01): A2 (Track A) closed. Used existing `*RepositoryDataJpaTest` naming over the plan's literal `*RepositoryTest` to stay uniform with the 10 already-shipped suites. AdminSettingRepository and UserSettingsRepository intentionally skipped — no custom queries.
- 2026-05-04 (plan 23-02): A7 closed. pitest-maven 1.17.4 wired behind an opt-in `mutation` profile with a 60% project-level threshold; the gate already passes at 63% kill rate (1043/1659). Per-class lift is deferred to ISS-100..ISS-109 — ReportService, AuthService, BudgetService, PriceSyncService, DebtService warrant dedicated plans, and BackupService / PushService / MailService need a constructor-injection refactor before they can be tested. CI mutation job is informational only (not in `ci-complete` needs[]).

### Deferred Issues

See `tasks/ROADMAP.md` "Won't do" list and Track G items not yet phased (G7-G10, G13-G16).
Newly-logged in `.planning/ISSUES.md` from plan 23-02:
- ISS-100..ISS-109: per-class mutation lift backlog for the 10 service classes still below the 60% per-class target captured in `23-02-BASELINE.md`.

### Blockers/Concerns

From `.planning/codebase/CONCERNS.md`, folded into upcoming phases:
- `WebClient.block()` + `Thread.sleep` in price clients → Phase 25
- Permissive CORS in production profile → Phase 24
- Optional Redis password → Phase 24
- AuditService coverage for domain mutations → Phase 24
- Per-asset delta WebSocket broadcast → Phase 30

## Session Continuity

Last session: 2026-05-04
Stopped at: Completed 23-02-PLAN.md (Track A7 — pitest mutation gate at 60% project-level; 10 per-class lifts deferred as ISS-100..ISS-109)
Resume file: None
