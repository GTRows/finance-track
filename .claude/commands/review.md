---
description: Review recent changes for quality, security, and consistency
---

Perform a code review of recent changes in: $ARGUMENTS

Check the following:

**Security:**
- No hardcoded secrets, passwords, or API keys
- All endpoints that should be authenticated are protected
- SQL queries use parameterized statements (no string concatenation)
- User input is validated with @Valid / Jakarta constraints

**Backend:**
- Controller methods are thin (no business logic)
- Service methods have @Transactional where they write to DB
- DTOs are Java records (not mutable classes)
- Errors are thrown as proper exceptions (not returned as strings)
- No N+1 query problems (check for missing JOIN FETCH)

**Frontend:**
- No `any` types in TypeScript
- Currency amounts use `formatTRY()` — never raw number formatting
- API calls go through `src/api/client.ts` — never raw fetch/axios
- Loading and error states are handled
- No sensitive data in localStorage or URL params

**Database:**
- New columns have appropriate constraints (NOT NULL, DEFAULT, etc.)
- New tables have appropriate indexes
- Migration file is new (not editing existing)

**General:**
- No console.log or System.out.println left in production code
- No TODO comments in code (move to tasks/TODO.md)
- Tests exist for new business logic

Report findings grouped by severity: Critical / Warning / Suggestion
