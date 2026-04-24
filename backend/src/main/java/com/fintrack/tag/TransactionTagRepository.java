package com.fintrack.tag;

import com.fintrack.common.entity.TransactionTag;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionTagRepository extends JpaRepository<TransactionTag, TransactionTag.PK> {

    List<TransactionTag> findByTransactionId(UUID transactionId);

    @Query("SELECT tt FROM TransactionTag tt WHERE tt.transactionId IN :transactionIds")
    List<TransactionTag> findByTransactionIds(
            @Param("transactionIds") Collection<UUID> transactionIds);

    @Modifying
    @Query("DELETE FROM TransactionTag tt WHERE tt.transactionId = :transactionId")
    void deleteByTransactionId(@Param("transactionId") UUID transactionId);

    @Modifying
    @Query(
            "DELETE FROM TransactionTag tt WHERE tt.transactionId = :transactionId AND tt.tagId ="
                    + " :tagId")
    void deleteByTransactionIdAndTagId(
            @Param("transactionId") UUID transactionId, @Param("tagId") UUID tagId);

    @Modifying
    @Query("DELETE FROM TransactionTag tt WHERE tt.tagId = :tagId")
    void deleteByTagId(@Param("tagId") UUID tagId);

    @Query(
            value = "SELECT COUNT(*) FROM transaction_tags WHERE tag_id = :tagId",
            nativeQuery = true)
    long countByTagId(@Param("tagId") UUID tagId);
}
