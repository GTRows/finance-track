package com.fintrack.watchlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.AssetRepository;
import com.fintrack.common.entity.WatchlistEntry;
import com.fintrack.common.exception.ResourceNotFoundException;
import com.fintrack.watchlist.dto.AddWatchlistRequest;
import com.fintrack.watchlist.dto.WatchlistEntryResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock WatchlistRepository repository;
    @Mock AssetRepository assetRepository;

    @InjectMocks WatchlistService service;

    private final UUID userId = UUID.randomUUID();

    private WatchlistEntry entry(UUID assetId, String note) {
        return WatchlistEntry.builder().userId(userId).assetId(assetId).note(note).build();
    }

    @Test
    void listReturnsMappedRows() {
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        when(repository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(entry(a1, "n1"), entry(a2, null)));

        List<WatchlistEntryResponse> res = service.list(userId);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).assetId()).isEqualTo(a1);
        assertThat(res.get(0).note()).isEqualTo("n1");
        assertThat(res.get(1).note()).isNull();
    }

    @Test
    void addRejectsUnknownAsset() {
        UUID assetId = UUID.randomUUID();
        when(assetRepository.existsById(assetId)).thenReturn(false);

        assertThatThrownBy(() -> service.add(userId, new AddWatchlistRequest(assetId, "note")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void addInsertsNewEntryWhenNotPresent() {
        UUID assetId = UUID.randomUUID();
        when(assetRepository.existsById(assetId)).thenReturn(true);
        when(repository.findByUserIdAndAssetId(userId, assetId)).thenReturn(Optional.empty());
        when(repository.save(any(WatchlistEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.add(userId, new AddWatchlistRequest(assetId, "my note"));

        ArgumentCaptor<WatchlistEntry> captor = ArgumentCaptor.forClass(WatchlistEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(captor.getValue().getNote()).isEqualTo("my note");
    }

    @Test
    void addUpdatesNoteWhenEntryAlreadyExists() {
        UUID assetId = UUID.randomUUID();
        WatchlistEntry existing = entry(assetId, "old");
        when(assetRepository.existsById(assetId)).thenReturn(true);
        when(repository.findByUserIdAndAssetId(userId, assetId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.add(userId, new AddWatchlistRequest(assetId, "new"));

        assertThat(existing.getNote()).isEqualTo("new");
        verify(repository).save(existing);
    }

    @Test
    void removeDelegatesToRepository() {
        UUID assetId = UUID.randomUUID();

        service.remove(userId, assetId);

        verify(repository).deleteByUserIdAndAssetId(userId, assetId);
    }
}
