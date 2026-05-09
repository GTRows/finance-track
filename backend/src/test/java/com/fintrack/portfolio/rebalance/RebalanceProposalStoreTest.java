package com.fintrack.portfolio.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RebalanceProposalStoreTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    @InjectMocks RebalanceProposalStore store;

    @Test
    void putProposal_writesValueWithThirtyMinuteTtl() {
        UUID userId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        String hash = "abc123";
        when(redis.opsForValue()).thenReturn(ops);

        store.putProposal(userId, proposalId, hash);

        String key = "rebalance:proposal:" + userId + ":" + proposalId;
        verify(ops).set(eq(key), eq(hash), eq(Duration.ofMinutes(30)));
    }

    @Test
    void getProposal_returnsHashWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("rebalance:proposal:" + userId + ":" + proposalId)).thenReturn("hashvalue");

        Optional<String> result = store.getProposal(userId, proposalId);

        assertThat(result).contains("hashvalue");
    }

    @Test
    void getProposal_returnsEmptyWhenAbsent() {
        UUID userId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("rebalance:proposal:" + userId + ":" + proposalId)).thenReturn(null);

        Optional<String> result = store.getProposal(userId, proposalId);

        assertThat(result).isEmpty();
    }

    @Test
    void markCommitted_writesSentinelAndDeletesOpenProposal() {
        UUID userId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(ops);

        store.markCommitted(userId, proposalId);

        String committedKey = "rebalance:committed:" + userId + ":" + proposalId;
        String proposalKey = "rebalance:proposal:" + userId + ":" + proposalId;
        verify(ops).set(eq(committedKey), eq("1"), eq(Duration.ofHours(24)));
        verify(redis).delete(proposalKey);
    }

    @Test
    void isCommitted_reflectsKeyPresence() {
        UUID userId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        String key = "rebalance:committed:" + userId + ":" + proposalId;
        when(redis.hasKey(key)).thenReturn(Boolean.TRUE);

        assertThat(store.isCommitted(userId, proposalId)).isTrue();

        when(redis.hasKey(key)).thenReturn(Boolean.FALSE);
        assertThat(store.isCommitted(userId, proposalId)).isFalse();
    }
}
