package com.fintrack.portfolio.rebalance;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed store for rebalance proposal hashes.
 *
 * <p>The preview endpoint stamps a proposalId, computes a canonical hash of the (portfolio,
 * account, suggestions) tuple, and saves the mapping with a 30-minute TTL. The commit endpoint
 * recomputes the hash from the live state and rejects mismatches as stale. Successful commits leave
 * a 24-hour sentinel so a double-commit attempt within that window returns a clean 409.
 */
@Component
@RequiredArgsConstructor
public class RebalanceProposalStore {

    static final String PROPOSAL_KEY_PREFIX = "rebalance:proposal:";
    static final String COMMITTED_KEY_PREFIX = "rebalance:committed:";
    static final Duration PROPOSAL_TTL = Duration.ofMinutes(30);
    static final Duration COMMITTED_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public void putProposal(UUID userId, UUID proposalId, String canonicalHash) {
        redis.opsForValue().set(proposalKey(userId, proposalId), canonicalHash, PROPOSAL_TTL);
    }

    public Optional<String> getProposal(UUID userId, UUID proposalId) {
        return Optional.ofNullable(redis.opsForValue().get(proposalKey(userId, proposalId)));
    }

    public void markCommitted(UUID userId, UUID proposalId) {
        redis.opsForValue().set(committedKey(userId, proposalId), "1", COMMITTED_TTL);
        redis.delete(proposalKey(userId, proposalId));
    }

    public boolean isCommitted(UUID userId, UUID proposalId) {
        return Boolean.TRUE.equals(redis.hasKey(committedKey(userId, proposalId)));
    }

    private static String proposalKey(UUID userId, UUID proposalId) {
        return PROPOSAL_KEY_PREFIX + userId + ":" + proposalId;
    }

    private static String committedKey(UUID userId, UUID proposalId) {
        return COMMITTED_KEY_PREFIX + userId + ":" + proposalId;
    }
}
