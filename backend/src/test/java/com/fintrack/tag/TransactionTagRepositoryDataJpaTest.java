package com.fintrack.tag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.auth.UserRepository;
import com.fintrack.budget.TransactionRepository;
import com.fintrack.common.AbstractDataJpaTestSupport;
import com.fintrack.common.entity.BudgetTransaction;
import com.fintrack.common.entity.Tag;
import com.fintrack.common.entity.TransactionTag;
import com.fintrack.common.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf("com.fintrack.common.AbstractDataJpaTestSupport#dockerAvailable")
class TransactionTagRepositoryDataJpaTest extends AbstractDataJpaTestSupport {

    @Autowired TransactionTagRepository repo;
    @Autowired TagRepository tagRepo;
    @Autowired TransactionRepository txnRepo;
    @Autowired UserRepository userRepo;

    private UUID seedUser(String username) {
        return userRepo.save(
                        User.builder()
                                .username(username)
                                .email(username + "@example.com")
                                .password("bcrypt-hash")
                                .role(User.Role.USER)
                                .build())
                .getId();
    }

    private UUID seedTxn(UUID userId) {
        return txnRepo.save(
                        BudgetTransaction.builder()
                                .userId(userId)
                                .txnType(BudgetTransaction.TxnType.EXPENSE)
                                .amount(new BigDecimal("100"))
                                .txnDate(LocalDate.of(2026, 4, 1))
                                .build())
                .getId();
    }

    private UUID seedTag(UUID userId, String name) {
        return tagRepo.save(Tag.builder().userId(userId).name(name).build()).getId();
    }

    private void link(UUID transactionId, UUID tagId) {
        repo.save(TransactionTag.builder().transactionId(transactionId).tagId(tagId).build());
    }

    @Test
    void findByTransactionIdReturnsAllLinks() {
        UUID userId = seedUser("ali");
        UUID txn = seedTxn(userId);
        link(txn, seedTag(userId, "groceries"));
        link(txn, seedTag(userId, "monthly"));

        assertThat(repo.findByTransactionId(txn)).hasSize(2);
    }

    @Test
    void findByTransactionIdsHandlesBatch() {
        UUID userId = seedUser("ada");
        UUID t1 = seedTxn(userId);
        UUID t2 = seedTxn(userId);
        UUID tag = seedTag(userId, "x");
        link(t1, tag);
        link(t2, tag);

        assertThat(repo.findByTransactionIds(List.of(t1, t2))).hasSize(2);
    }

    @Test
    void deleteByTransactionIdRemovesAllLinksForTxn() {
        UUID userId = seedUser("kemal");
        UUID txn = seedTxn(userId);
        link(txn, seedTag(userId, "a"));
        link(txn, seedTag(userId, "b"));

        repo.deleteByTransactionId(txn);

        assertThat(repo.findByTransactionId(txn)).isEmpty();
    }

    @Test
    void deleteByTransactionIdAndTagIdRemovesSingle() {
        UUID userId = seedUser("ozge");
        UUID txn = seedTxn(userId);
        UUID keep = seedTag(userId, "keep");
        UUID drop = seedTag(userId, "drop");
        link(txn, keep);
        link(txn, drop);

        repo.deleteByTransactionIdAndTagId(txn, drop);

        assertThat(repo.findByTransactionId(txn))
                .extracting(TransactionTag::getTagId)
                .containsExactly(keep);
    }

    @Test
    void deleteByTagIdRemovesAcrossTransactions() {
        UUID userId = seedUser("yusuf");
        UUID tag = seedTag(userId, "shared");
        UUID t1 = seedTxn(userId);
        UUID t2 = seedTxn(userId);
        link(t1, tag);
        link(t2, tag);

        repo.deleteByTagId(tag);

        assertThat(repo.findByTransactionId(t1)).isEmpty();
        assertThat(repo.findByTransactionId(t2)).isEmpty();
    }

    @Test
    void countByTagIdNativeReturnsCount() {
        UUID userId = seedUser("baris");
        UUID tag = seedTag(userId, "popular");
        link(seedTxn(userId), tag);
        link(seedTxn(userId), tag);
        link(seedTxn(userId), tag);

        assertThat(repo.countByTagId(tag)).isEqualTo(3L);
    }

    @Test
    void countByTagIdReturnsZeroWhenUnused() {
        assertThat(repo.countByTagId(UUID.randomUUID())).isZero();
    }
}
