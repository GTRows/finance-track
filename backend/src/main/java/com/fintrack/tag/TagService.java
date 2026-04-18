package com.fintrack.tag;

import com.fintrack.common.entity.Tag;
import com.fintrack.common.entity.TransactionTag;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.tag.dto.TagResponse;
import com.fintrack.tag.dto.UpsertTagRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepo;
    private final TransactionTagRepository txnTagRepo;

    @Transactional(readOnly = true)
    public List<TagResponse> list(UUID userId) {
        List<Tag> tags = tagRepo.findByUserIdOrderByNameAsc(userId);
        if (tags.isEmpty()) return List.of();

        Map<UUID, Long> counts = tags.stream()
                .collect(Collectors.toMap(Tag::getId, t -> txnTagRepo.countByTagId(t.getId())));

        return tags.stream()
                .map(t -> TagResponse.from(t, counts.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    @Transactional
    public TagResponse create(UUID userId, UpsertTagRequest req) {
        String name = req.name().trim();
        tagRepo.findByUserIdAndName(userId, name).ifPresent(t -> {
            throw new BusinessRuleException("Tag with this name already exists", "TAG_DUPLICATE");
        });

        Tag tag = Tag.builder()
                .userId(userId)
                .name(name)
                .color(req.color())
                .build();
        tag = tagRepo.save(tag);
        log.info("Tag created: id={} name={}", tag.getId(), tag.getName());
        return TagResponse.from(tag, 0L);
    }

    @Transactional
    public TagResponse update(UUID userId, UUID tagId, UpsertTagRequest req) {
        Tag tag = tagRepo.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found"));

        String name = req.name().trim();
        if (!name.equals(tag.getName())) {
            tagRepo.findByUserIdAndName(userId, name).ifPresent(existing -> {
                if (!existing.getId().equals(tagId)) {
                    throw new BusinessRuleException("Tag with this name already exists", "TAG_DUPLICATE");
                }
            });
            tag.setName(name);
        }
        tag.setColor(req.color());
        tag = tagRepo.save(tag);
        return TagResponse.from(tag, txnTagRepo.countByTagId(tag.getId()));
    }

    @Transactional
    public void delete(UUID userId, UUID tagId) {
        Tag tag = tagRepo.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found"));
        txnTagRepo.deleteByTagId(tag.getId());
        tagRepo.delete(tag);
        log.info("Tag deleted: id={}", tagId);
    }

    // -- Internal helpers consumed by BudgetService --

    /** Resolve and validate tag ids the user owns; silently drop ids that don't belong. */
    @Transactional(readOnly = true)
    public List<UUID> resolveOwnedIds(UUID userId, List<UUID> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return List.of();
        return tagRepo.findAllByIdInAndUserId(candidateIds, userId).stream()
                .map(Tag::getId)
                .toList();
    }

    /** Replace the tag set attached to a transaction. */
    @Transactional
    public void setTransactionTags(UUID transactionId, List<UUID> tagIds) {
        txnTagRepo.deleteByTransactionId(transactionId);
        if (tagIds == null || tagIds.isEmpty()) return;
        for (UUID tagId : new LinkedHashSet<>(tagIds)) {
            txnTagRepo.save(TransactionTag.builder()
                    .transactionId(transactionId)
                    .tagId(tagId)
                    .build());
        }
    }

    /**
     * Additive / subtractive change to a transaction's tag set, skipping tags
     * already in the desired state. Used by bulk-update where the caller wants
     * "add these, remove these, leave everything else alone".
     */
    @Transactional
    public void mutateTransactionTags(UUID transactionId, List<UUID> addIds, List<UUID> removeIds) {
        Set<UUID> current = txnTagRepo.findByTransactionId(transactionId).stream()
                .map(TransactionTag::getTagId)
                .collect(Collectors.toSet());
        if (addIds != null) {
            for (UUID tagId : new LinkedHashSet<>(addIds)) {
                if (current.add(tagId)) {
                    txnTagRepo.save(TransactionTag.builder()
                            .transactionId(transactionId)
                            .tagId(tagId)
                            .build());
                }
            }
        }
        if (removeIds != null && !removeIds.isEmpty()) {
            for (UUID tagId : removeIds) {
                if (current.contains(tagId)) {
                    txnTagRepo.deleteByTransactionIdAndTagId(transactionId, tagId);
                }
            }
        }
    }

    /** Bulk-load tag DTOs indexed by transaction id for a list of transactions. */
    @Transactional(readOnly = true)
    public Map<UUID, List<TagSummary>> loadTagsForTransactions(UUID userId, Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) return Map.of();
        List<TransactionTag> joins = txnTagRepo.findByTransactionIds(transactionIds);
        if (joins.isEmpty()) return Map.of();

        List<UUID> tagIds = joins.stream().map(TransactionTag::getTagId).distinct().toList();
        Map<UUID, Tag> tagsById = tagRepo.findAllByIdInAndUserId(tagIds, userId).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<UUID, List<TagSummary>> out = new HashMap<>();
        for (TransactionTag tt : joins) {
            Tag tag = tagsById.get(tt.getTagId());
            if (tag == null) continue;
            out.computeIfAbsent(tt.getTransactionId(), k -> new ArrayList<>())
                    .add(new TagSummary(tag.getId(), tag.getName(), tag.getColor()));
        }
        out.values().forEach(list -> list.sort(Comparator.comparing(TagSummary::name, String.CASE_INSENSITIVE_ORDER)));
        return out;
    }

    public record TagSummary(UUID id, String name, String color) {}
}
