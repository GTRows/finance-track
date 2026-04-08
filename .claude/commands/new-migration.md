---
description: Create a new Flyway database migration
---

Create a new Flyway migration for: $ARGUMENTS

Steps:
1. Read `docs/DATABASE.md` to understand existing schema
2. Find the latest migration version in `backend/src/main/resources/db/migration/`
3. Create `V{n+1}__{snake_case_description}.sql`
4. Write the migration SQL:
   - Use `IF NOT EXISTS` for safety where applicable
   - Add indexes for all foreign keys and commonly queried columns
   - Include comments explaining non-obvious decisions
   - Never modify existing tables destructively without a backup plan
5. Update `docs/DATABASE.md` to reflect schema changes
6. Add corresponding JPA entity changes if needed
7. Never edit existing migration files — always add a new one
