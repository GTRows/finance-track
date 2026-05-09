package com.fintrack.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.PostgresDataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pins the FK-index coverage contract on the public schema. A regression here surfaces a future
 * migration that adds a foreign-key column without an accompanying supporting index. Sentinel test
 * for Phase 30 sub-plan 02.
 */
@PostgresDataJpaTest
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class IndexCoverageRegressionTest extends AbstractDataJpaTestSupport {

    @Autowired EntityManager entityManager;

    /**
     * (table, column) pairs that are known FK columns whose reverse-scan path is intentionally not
     * covered by an index. The motivating tables are tiny at single-user scale (a few dozen rows at
     * most) so the sequential-scan cost on a parent delete is sub-millisecond. Adding an index here
     * would burn write amplification with no measurable read win. Each entry must remain justified
     * in the V47 inspection notes.
     */
    private static final Set<String> ACCEPTED_UNINDEXED_FK_COLUMNS =
            Set.of(
                    // V1 income/expense category tables: cascaded only on the user-delete path,
                    // and the table holds < 30 rows per user.
                    "income_categories.user_id",
                    "expense_categories.user_id",
                    // V10 budget_rules.category_id: cascaded only when the (small) per-user
                    // expense_categories row is deleted; UNIQUE(user_id, category_id) leads with
                    // user_id so equality on category_id alone falls back to a seq scan, which is
                    // sub-millisecond at this row count.
                    "budget_rules.category_id",
                    // V17 watchlist_entries.asset_id: UNIQUE(user_id, asset_id) leads with
                    // user_id; asset_id-only reverse scans only fire on asset deletion which is
                    // operator-driven and very rare.
                    "watchlist_entries.asset_id");

    /**
     * Index names the V47 migration shipped. Hard-coded by name; a future drive-by `DROP INDEX`
     * fails this test and surfaces the loss before it reaches production. Keep this list in sync
     * with `V47__index_audit_phase30.sql`.
     */
    private static final Set<String> V47_INDEX_NAMES =
            Set.of(
                    "idx_holdings_asset_id",
                    "idx_price_alerts_asset_id",
                    "idx_alert_notifications_alert_id",
                    "idx_alert_notifications_asset_id",
                    "idx_savings_goals_linked_portfolio_id");

    @Test
    @SuppressWarnings("unchecked")
    void everyForeignKeyColumnIsCoveredByALeadingColumnIndex() {
        // Resolve every FK column on the public schema, then confirm at least one index on the
        // same table starts with that column. A parent-delete reverse scan, or any equality
        // predicate on the FK column alone, otherwise falls back to a sequential scan.
        List<Object[]> fkRows =
                entityManager
                        .createNativeQuery(
                                "SELECT kcu.table_name, kcu.column_name "
                                        + "FROM information_schema.referential_constraints rc "
                                        + "JOIN information_schema.key_column_usage kcu "
                                        + "  ON kcu.constraint_name = rc.constraint_name "
                                        + " AND kcu.constraint_schema = rc.constraint_schema "
                                        + "WHERE rc.constraint_schema = 'public'")
                        .getResultList();

        List<String> uncovered = new ArrayList<>();
        for (Object[] row : fkRows) {
            String table = (String) row[0];
            String column = (String) row[1];
            String key = table + "." + column;
            if (ACCEPTED_UNINDEXED_FK_COLUMNS.contains(key)) {
                continue;
            }
            if (!leadingColumnIndexExists(table, column)) {
                uncovered.add(key);
            }
        }

        assertThat(uncovered)
                .as(
                        "Foreign-key columns missing a leading-column index. Add the missing"
                                + " index in a forward Flyway migration, or, if the seq-scan cost"
                                + " is intentionally accepted at single-user scale, document the"
                                + " (table, column) in the test's ACCEPTED_UNINDEXED_FK_COLUMNS"
                                + " set with a justification.")
                .isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void v47IndexesArePresentByName() {
        List<String> indexNames =
                entityManager
                        .createNativeQuery(
                                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
                        .getResultList();
        Set<String> existing = new HashSet<>(indexNames);

        for (String expected : V47_INDEX_NAMES) {
            assertThat(existing)
                    .as(
                            "V47-shipped index '%s' must exist; a drive-by DROP INDEX would lose"
                                    + " the FK-coverage contract for Phase 30 sub-plan 02.",
                            expected)
                    .contains(expected);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void noDuplicateIndexCoversTheSameLeadingColumnTuple() {
        // Group every non-PK / non-unique index by (table, leading_column) and assert at most one
        // per group. Catches accidental drive-by index duplication. Unique and primary-key
        // indexes are whitelisted because the unique-or-primary-key index legitimately leads with
        // the same column as a non-unique partial index in several established patterns
        // (V35 totp_recovery_codes user_id + partial WHERE consumed_at IS NULL, V41 accounts
        // user_id partial unique + (user_id, is_archived) composite, etc).
        List<Object[]> rows =
                entityManager
                        .createNativeQuery(
                                "SELECT c.relname AS table_name, "
                                        + "       i.relname AS index_name, "
                                        + "       a.attname AS leading_column "
                                        + "FROM pg_index ix "
                                        + "JOIN pg_class i ON i.oid = ix.indexrelid "
                                        + "JOIN pg_class c ON c.oid = ix.indrelid "
                                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                                        + "JOIN pg_attribute a ON a.attrelid = c.oid"
                                        + "                  AND a.attnum = ix.indkey[0] "
                                        + "WHERE n.nspname = 'public' "
                                        + "  AND ix.indisunique = false "
                                        + "  AND ix.indisprimary = false")
                        .getResultList();

        java.util.Map<String, List<String>> groups = new java.util.HashMap<>();
        for (Object[] row : rows) {
            String table = (String) row[0];
            String index = (String) row[1];
            String column = (String) row[2];
            String key = table + "." + column;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(index);
        }

        List<String> duplicates = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.add(entry.getKey() + " -> " + entry.getValue());
            }
        }

        assertThat(duplicates)
                .as(
                        "Two or more non-unique non-primary-key indexes lead with the same"
                                + " (table, column) tuple. This is almost always accidental"
                                + " redundancy. Drop one of the indexes in a forward Flyway"
                                + " migration, or, if both are deliberately retained (e.g. one is"
                                + " a partial index narrower than the other), justify the pair"
                                + " in this test's whitelist.")
                .isEmpty();
    }

    private boolean leadingColumnIndexExists(String table, String column) {
        Object count =
                entityManager
                        .createNativeQuery(
                                "SELECT COUNT(*) FROM pg_index ix "
                                        + "JOIN pg_class c ON c.oid = ix.indrelid "
                                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                                        + "JOIN pg_attribute a ON a.attrelid = c.oid "
                                        + "                  AND a.attnum = ix.indkey[0] "
                                        + "WHERE n.nspname = 'public' "
                                        + "  AND c.relname = :table "
                                        + "  AND a.attname = :column")
                        .setParameter("table", table)
                        .setParameter("column", column)
                        .getSingleResult();
        long n = ((Number) count).longValue();
        return n > 0;
    }
}
