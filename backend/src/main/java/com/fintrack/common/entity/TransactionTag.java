package com.fintrack.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transaction_tags")
@IdClass(TransactionTag.PK.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionTag {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Id
    @Column(name = "tag_id")
    private UUID tagId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private UUID transactionId;
        private UUID tagId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(transactionId, pk.transactionId) && Objects.equals(tagId, pk.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(transactionId, tagId);
        }
    }
}
