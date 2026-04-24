package com.fintrack.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.asset.TefasFundService.FundSearchRow;
import com.fintrack.common.entity.Asset;
import com.fintrack.common.entity.Asset.AssetType;
import com.fintrack.price.PriceSyncService;
import com.fintrack.price.client.TefasClient;
import com.fintrack.price.client.TefasClient.FundSummary;
import com.fintrack.price.client.TefasClient.FundType;
import java.util.HashMap;
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
class TefasFundServiceTest {

    @Mock TefasClient tefasClient;
    @Mock AssetRepository assetRepository;
    @Mock PriceSyncService priceSyncService;

    @InjectMocks TefasFundService service;

    private FundSummary fund(String code, String name, FundType type) {
        return new FundSummary(code, name, type);
    }

    private Asset fundAsset(String symbol, Map<String, Object> metadata) {
        return Asset.builder()
                .id(UUID.randomUUID())
                .symbol(symbol)
                .name(symbol)
                .assetType(AssetType.FUND)
                .currency("TRY")
                .metadata(metadata)
                .build();
    }

    @Test
    void searchRejectsTooShortQuery() {
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search(" ")).isEmpty();
        assertThat(service.search("x")).isEmpty();
    }

    @Test
    void searchMatchesCodeOrNameSubstringAcrossCatalogs() {
        when(tefasClient.listAll(FundType.YAT))
                .thenReturn(
                        List.of(
                                fund("TTA", "TEB PARA PIYASASI", FundType.YAT),
                                fund("ABC", "Some other fund", FundType.YAT)));
        when(tefasClient.listAll(FundType.EMK))
                .thenReturn(List.of(fund("BES1", "Is Bankasi Emeklilik", FundType.EMK)));
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND)).thenReturn(List.of());

        List<FundSearchRow> res = service.search("teb");

        assertThat(res).hasSize(1);
        assertThat(res.get(0).code()).isEqualTo("TTA");
        assertThat(res.get(0).imported()).isFalse();
    }

    @Test
    void searchSetsImportedTrueWhenAssetAlreadyExists() {
        when(tefasClient.listAll(FundType.YAT))
                .thenReturn(List.of(fund("TTA", "TEB PARA PIYASASI", FundType.YAT)));
        when(tefasClient.listAll(FundType.EMK)).thenReturn(List.of());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tefasCode", "TTA");
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND))
                .thenReturn(List.of(fundAsset("TTA", metadata)));

        FundSearchRow row = service.search("TTA").get(0);

        assertThat(row.imported()).isTrue();
    }

    @Test
    void searchCapsResultsAt25() {
        List<FundSummary> many = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(fund("F" + i, "Test Fund " + i, FundType.YAT));
        }
        when(tefasClient.listAll(FundType.YAT)).thenReturn(many);
        when(tefasClient.listAll(FundType.EMK)).thenReturn(List.of());
        when(assetRepository.findByAssetTypeOrderBySymbolAsc(AssetType.FUND)).thenReturn(List.of());

        assertThat(service.search("test")).hasSize(25);
    }

    @Test
    void importFundRejectsBlankCode() {
        assertThatThrownBy(() -> service.importFund(" ", FundType.YAT))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.importFund(null, FundType.YAT))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void importFundRefreshesWhenAssetAlreadyPresent() {
        Asset existing = fundAsset("TTA", Map.of("tefasCode", "TTA"));
        when(assetRepository.findBySymbolAndAssetType("TTA", AssetType.FUND))
                .thenReturn(Optional.of(existing));
        when(assetRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        Asset res = service.importFund("tta", FundType.YAT);

        assertThat(res).isSameAs(existing);
        verify(priceSyncService).refreshAsset(existing.getId());
        verify(assetRepository, never()).save(any());
    }

    @Test
    void importFundSavesNewAssetAndRefreshesPrice() {
        when(assetRepository.findBySymbolAndAssetType("TTA", AssetType.FUND))
                .thenReturn(Optional.empty());
        when(tefasClient.listAll(FundType.YAT))
                .thenReturn(List.of(fund("TTA", "TEB PARA PIYASASI", FundType.YAT)));
        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(
                        inv -> {
                            Asset a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });
        when(assetRepository.findById(any(UUID.class)))
                .thenAnswer(
                        inv -> {
                            Asset a =
                                    Asset.builder()
                                            .id(inv.getArgument(0))
                                            .symbol("TTA")
                                            .name("TEB PARA PIYASASI")
                                            .assetType(AssetType.FUND)
                                            .currency("TRY")
                                            .build();
                            return Optional.of(a);
                        });

        Asset saved = service.importFund("  tta  ", FundType.YAT);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset created = captor.getValue();
        assertThat(created.getSymbol()).isEqualTo("TTA");
        assertThat(created.getName()).isEqualTo("TEB PARA PIYASASI");
        assertThat(created.getCurrency()).isEqualTo("TRY");
        assertThat(created.getMetadata())
                .containsEntry("tefasCode", "TTA")
                .containsEntry("tefasType", "YAT")
                .containsEntry("source", "tefas-import");
        verify(priceSyncService).refreshAsset(saved.getId());
    }

    @Test
    void importFundFallsBackToCodeAsNameWhenNotFound() {
        when(assetRepository.findBySymbolAndAssetType("XYZ", AssetType.FUND))
                .thenReturn(Optional.empty());
        when(tefasClient.listAll(FundType.YAT)).thenReturn(List.of());
        when(tefasClient.listAll(FundType.EMK)).thenReturn(List.of());
        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(
                        inv -> {
                            Asset a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });
        when(assetRepository.findById(any(UUID.class)))
                .thenAnswer(
                        inv -> {
                            Asset a =
                                    Asset.builder()
                                            .id(inv.getArgument(0))
                                            .symbol("XYZ")
                                            .name("XYZ")
                                            .assetType(AssetType.FUND)
                                            .currency("TRY")
                                            .build();
                            return Optional.of(a);
                        });

        service.importFund("xyz", FundType.YAT);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("XYZ");
    }

    @Test
    void importFundChecksOtherCatalogIfPrimaryMisses() {
        when(assetRepository.findBySymbolAndAssetType("BES1", AssetType.FUND))
                .thenReturn(Optional.empty());
        when(tefasClient.listAll(FundType.YAT)).thenReturn(List.of());
        when(tefasClient.listAll(FundType.EMK))
                .thenReturn(List.of(fund("BES1", "Is Bankasi Emeklilik", FundType.EMK)));
        when(assetRepository.save(any(Asset.class)))
                .thenAnswer(
                        inv -> {
                            Asset a = inv.getArgument(0);
                            a.setId(UUID.randomUUID());
                            return a;
                        });
        when(assetRepository.findById(any(UUID.class)))
                .thenAnswer(
                        inv -> {
                            Asset a = Asset.builder().id(inv.getArgument(0)).build();
                            return Optional.of(a);
                        });

        service.importFund("bes1", FundType.YAT);

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Is Bankasi Emeklilik");
    }
}
