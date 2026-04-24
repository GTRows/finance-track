package com.fintrack.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.common.entity.Tag;
import com.fintrack.common.entity.TransactionTag;
import com.fintrack.common.exception.BusinessRuleException;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.tag.dto.TagResponse;
import com.fintrack.tag.dto.UpsertTagRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepo;
    @Mock TransactionTagRepository txnTagRepo;

    @InjectMocks TagService service;

    private final UUID userId = UUID.randomUUID();

    private Tag tag(String name, String color) {
        return Tag.builder().id(UUID.randomUUID()).userId(userId).name(name).color(color).build();
    }

    @Test
    void listReturnsEmptyWhenNoTags() {
        when(tagRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());

        assertThat(service.list(userId)).isEmpty();
        verify(txnTagRepo, never()).countByTagId(any());
    }

    @Test
    void listReturnsTagsWithUsageCounts() {
        Tag t1 = tag("food", "#f00");
        Tag t2 = tag("travel", "#0f0");
        when(tagRepo.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(t1, t2));
        when(txnTagRepo.countByTagId(t1.getId())).thenReturn(3L);
        when(txnTagRepo.countByTagId(t2.getId())).thenReturn(0L);

        List<TagResponse> res = service.list(userId);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).name()).isEqualTo("food");
        assertThat(res.get(0).usageCount()).isEqualTo(3L);
        assertThat(res.get(1).usageCount()).isZero();
    }

    @Test
    void createTrimsNameAndPersists() {
        when(tagRepo.findByUserIdAndName(eq(userId), eq("food"))).thenReturn(Optional.empty());
        when(tagRepo.save(any(Tag.class)))
                .thenAnswer(
                        inv -> {
                            Tag in = inv.getArgument(0);
                            in.setId(UUID.randomUUID());
                            return in;
                        });

        TagResponse res = service.create(userId, new UpsertTagRequest("  food  ", "#abc"));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepo).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("food");
        assertThat(captor.getValue().getColor()).isEqualTo("#abc");
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(res.usageCount()).isZero();
    }

    @Test
    void createRejectsDuplicateName() {
        when(tagRepo.findByUserIdAndName(userId, "food"))
                .thenReturn(Optional.of(tag("food", null)));

        assertThatThrownBy(() -> service.create(userId, new UpsertTagRequest("food", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");

        verify(tagRepo, never()).save(any());
    }

    @Test
    void updateChangesNameAndColorWhenNotDuplicate() {
        Tag existing = tag("old", "#111");
        when(tagRepo.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));
        when(tagRepo.findByUserIdAndName(userId, "new")).thenReturn(Optional.empty());
        when(tagRepo.save(existing)).thenReturn(existing);
        when(txnTagRepo.countByTagId(existing.getId())).thenReturn(2L);

        TagResponse res =
                service.update(userId, existing.getId(), new UpsertTagRequest("new", "#222"));

        assertThat(existing.getName()).isEqualTo("new");
        assertThat(existing.getColor()).isEqualTo("#222");
        assertThat(res.usageCount()).isEqualTo(2L);
    }

    @Test
    void updateAllowsSameNameSinceIdMatches() {
        Tag existing = tag("food", "#111");
        when(tagRepo.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));
        when(tagRepo.save(existing)).thenReturn(existing);
        when(txnTagRepo.countByTagId(existing.getId())).thenReturn(1L);

        service.update(userId, existing.getId(), new UpsertTagRequest("food", "#999"));

        assertThat(existing.getColor()).isEqualTo("#999");
    }

    @Test
    void updateRejectsNameCollisionWithOtherTag() {
        Tag existing = tag("old", "#111");
        Tag other = tag("new", "#222");
        when(tagRepo.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));
        when(tagRepo.findByUserIdAndName(userId, "new")).thenReturn(Optional.of(other));

        assertThatThrownBy(
                        () ->
                                service.update(
                                        userId,
                                        existing.getId(),
                                        new UpsertTagRequest("new", null)))
                .isInstanceOf(BusinessRuleException.class);

        verify(tagRepo, never()).save(any());
    }

    @Test
    void updateThrowsWhenTagMissing() {
        UUID id = UUID.randomUUID();
        when(tagRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, id, new UpsertTagRequest("x", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesJoinRowsFirstThenTag() {
        Tag existing = tag("food", null);
        when(tagRepo.findByIdAndUserId(existing.getId(), userId)).thenReturn(Optional.of(existing));

        service.delete(userId, existing.getId());

        verify(txnTagRepo).deleteByTagId(existing.getId());
        verify(tagRepo).delete(existing);
    }

    @Test
    void deleteThrowsWhenTagMissing() {
        UUID id = UUID.randomUUID();
        when(tagRepo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(txnTagRepo, never()).deleteByTagId(any());
    }

    @Test
    void resolveOwnedIdsReturnsEmptyForNullOrEmpty() {
        assertThat(service.resolveOwnedIds(userId, null)).isEmpty();
        assertThat(service.resolveOwnedIds(userId, List.of())).isEmpty();
    }

    @Test
    void resolveOwnedIdsReturnsOnlyTagsOwnedByUser() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Tag owned = Tag.builder().id(a).userId(userId).name("a").build();
        when(tagRepo.findAllByIdInAndUserId(List.of(a, b, c), userId)).thenReturn(List.of(owned));

        assertThat(service.resolveOwnedIds(userId, List.of(a, b, c))).containsExactly(a);
    }

    @Test
    void setTransactionTagsClearsThenInsertsDeduplicated() {
        UUID txnId = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();

        service.setTransactionTags(txnId, List.of(t1, t2, t1));

        verify(txnTagRepo).deleteByTransactionId(txnId);
        ArgumentCaptor<TransactionTag> captor = ArgumentCaptor.forClass(TransactionTag.class);
        verify(txnTagRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TransactionTag::getTagId)
                .containsExactly(t1, t2);
    }

    @Test
    void setTransactionTagsSkipsInsertsWhenListEmpty() {
        UUID txnId = UUID.randomUUID();

        service.setTransactionTags(txnId, List.of());

        verify(txnTagRepo).deleteByTransactionId(txnId);
        verify(txnTagRepo, never()).save(any());
    }

    @Test
    void mutateTransactionTagsAddsOnlyNewAndRemovesOnlyCurrent() {
        UUID txnId = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        UUID toAdd = UUID.randomUUID();
        UUID notPresent = UUID.randomUUID();

        TransactionTag current =
                TransactionTag.builder().transactionId(txnId).tagId(existing).build();
        when(txnTagRepo.findByTransactionId(txnId)).thenReturn(List.of(current));

        service.mutateTransactionTags(
                txnId, List.of(toAdd, existing), List.of(existing, notPresent));

        ArgumentCaptor<TransactionTag> saved = ArgumentCaptor.forClass(TransactionTag.class);
        verify(txnTagRepo).save(saved.capture());
        assertThat(saved.getValue().getTagId()).isEqualTo(toAdd);

        verify(txnTagRepo).deleteByTransactionIdAndTagId(txnId, existing);
        verify(txnTagRepo, never()).deleteByTransactionIdAndTagId(txnId, notPresent);
    }

    @Test
    void loadTagsForTransactionsReturnsEmptyWhenNoTransactions() {
        assertThat(service.loadTagsForTransactions(userId, List.of())).isEmpty();
    }

    @Test
    void loadTagsForTransactionsGroupsAndSortsByNameCaseInsensitive() {
        UUID txnA = UUID.randomUUID();
        UUID txnB = UUID.randomUUID();
        Tag food = tag("food", "#f00");
        Tag apple = tag("Apple", "#0f0");
        Tag travel = tag("travel", "#00f");

        List<TransactionTag> joins =
                List.of(
                        TransactionTag.builder().transactionId(txnA).tagId(food.getId()).build(),
                        TransactionTag.builder().transactionId(txnA).tagId(apple.getId()).build(),
                        TransactionTag.builder().transactionId(txnB).tagId(travel.getId()).build());
        when(txnTagRepo.findByTransactionIds(List.of(txnA, txnB))).thenReturn(joins);
        when(tagRepo.findAllByIdInAndUserId(any(), eq(userId)))
                .thenReturn(List.of(food, apple, travel));

        Map<UUID, List<TagService.TagSummary>> out =
                service.loadTagsForTransactions(userId, List.of(txnA, txnB));

        assertThat(out.get(txnA))
                .extracting(TagService.TagSummary::name)
                .containsExactly("Apple", "food");
        assertThat(out.get(txnB)).extracting(TagService.TagSummary::name).containsExactly("travel");
    }

    @Test
    void loadTagsForTransactionsDropsTagsNotOwnedByUser() {
        UUID txn = UUID.randomUUID();
        Tag food = tag("food", "#f00");
        UUID orphanTagId = UUID.randomUUID();

        List<TransactionTag> joins =
                List.of(
                        TransactionTag.builder().transactionId(txn).tagId(food.getId()).build(),
                        TransactionTag.builder().transactionId(txn).tagId(orphanTagId).build());
        when(txnTagRepo.findByTransactionIds(List.of(txn))).thenReturn(joins);
        when(tagRepo.findAllByIdInAndUserId(any(), eq(userId))).thenReturn(List.of(food));

        Map<UUID, List<TagService.TagSummary>> out =
                service.loadTagsForTransactions(userId, List.of(txn));

        assertThat(out.get(txn)).hasSize(1);
        assertThat(out.get(txn).get(0).name()).isEqualTo("food");
    }
}
