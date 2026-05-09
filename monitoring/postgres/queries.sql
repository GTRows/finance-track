-- Operator helper queries for the pg_stat_statements opt-in overlay.
-- Used after bringing up Postgres via
--   docker compose -f docker-compose.yml \
--     -f monitoring/postgres/docker-compose.postgres.yml up -d postgres
-- and running CREATE EXTENSION IF NOT EXISTS pg_stat_statements; once
-- per database lifetime. See docs/OPERATIONS.md
-- "Inspecting slow queries with pg_stat_statements" for the runbook.

-- Top 20 by total execution time
SELECT
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;

-- Reset between investigations
-- SELECT pg_stat_statements_reset();

-- EXPLAIN template -- replace <statement> with the JPQL-rendered SQL
-- pulled from `spring.jpa.properties.hibernate.show_sql=true` logs.
-- EXPLAIN (ANALYZE, BUFFERS) <statement>;
