package com.fintrack.account;

import com.fintrack.common.entity.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** JPA repository for {@link Account} entities. */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Returns all live (non-archived) accounts for a user, ordered by creation time. */
    List<Account> findByUserIdAndArchivedFalseOrderByCreatedAtAsc(UUID userId);

    /** Finds a live account by id and user id (ownership check). */
    Optional<Account> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);

    /** Counts live accounts for a user (used for the per-user cap). */
    long countByUserIdAndArchivedFalse(UUID userId);

    /** Counts archived accounts for a user (used by the totals strip). */
    long countByUserIdAndArchivedTrue(UUID userId);

    /** Case-insensitive duplicate-name guard restricted to live rows. */
    boolean existsByUserIdAndNameIgnoreCaseAndArchivedFalse(UUID userId, String name);

    /**
     * Per-currency live-balance rollup for the totals strip. Returns one row per distinct currency:
     * {currency, totalBalance}.
     */
    @Query(
            "SELECT a.currency, COALESCE(SUM(a.currentBalance), 0) FROM Account a "
                    + "WHERE a.userId = :userId AND a.archived = false "
                    + "GROUP BY a.currency "
                    + "ORDER BY a.currency ASC")
    List<Object[]> sumBalancesByCurrencyForUser(UUID userId);
}
