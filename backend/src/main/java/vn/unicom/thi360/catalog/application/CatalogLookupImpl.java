package vn.unicom.thi360.catalog.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.api.CatalogDescendantNodeResponse;
import vn.unicom.thi360.catalog.domain.CatalogGroup;
import vn.unicom.thi360.catalog.domain.CatalogItem;
import vn.unicom.thi360.catalog.domain.CatalogNormalizer;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Service
public class CatalogLookupImpl implements CatalogLookup {

    private final CatalogGroupRepository catalogGroupRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final CatalogSupportDao catalogSupportDao;
    private final CatalogQueryService catalogQueryService;

    public CatalogLookupImpl(
            CatalogGroupRepository catalogGroupRepository,
            CatalogItemRepository catalogItemRepository,
            CatalogSupportDao catalogSupportDao,
            CatalogQueryService catalogQueryService) {
        this.catalogGroupRepository = catalogGroupRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogSupportDao = catalogSupportDao;
        this.catalogQueryService = catalogQueryService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogItemView> findActiveItem(String groupCode, String itemCode) {
        String normGroup = CatalogNormalizer.normalizeCode(groupCode);
        String normItem = CatalogNormalizer.normalizeCode(itemCode);
        if (normGroup == null || normItem == null) {
            return Optional.empty();
        }
        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normGroup).orElse(null);
        if (group == null || group.getStatus() != CatalogStatus.ACTIVE) {
            return Optional.empty();
        }
        CatalogItem item = catalogItemRepository
                .findByGroupIdAndCodeNormalized(group.getId(), normItem)
                .orElse(null);
        if (item == null || item.getStatus() != CatalogStatus.ACTIVE) {
            return Optional.empty();
        }
        Set<UUID> inactive =
                catalogSupportDao.findInactiveEffectiveItemIds(group.getId(), List.of(item.getId()));
        if (inactive.contains(item.getId())) {
            return Optional.empty();
        }
        return Optional.of(toView(group, item, CatalogStatus.ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogItemView> findItem(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        CatalogItem item = catalogItemRepository.findById(id).orElse(null);
        if (item == null) {
            return Optional.empty();
        }
        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId()).orElse(null);
        if (group == null) {
            return Optional.empty();
        }
        Set<UUID> inactive =
                catalogSupportDao.findInactiveEffectiveItemIds(group.getId(), List.of(item.getId()));
        CatalogStatus effectiveStatus = inactive.contains(item.getId())
                ? CatalogStatus.INACTIVE
                : CatalogStatus.ACTIVE;
        return Optional.of(toView(group, item, effectiveStatus));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findLeafIdsUnder(UUID nodeId) {
        if (nodeId == null) {
            return List.of();
        }
        CatalogItem node = catalogItemRepository.findById(nodeId).orElse(null);
        if (node == null) {
            return List.of();
        }
        CatalogGroup group = catalogGroupRepository.findById(node.getGroupId()).orElse(null);
        if (group == null
                || group.getKind() != CatalogGroupKind.TREE
                || group.getStatus() != CatalogStatus.ACTIVE) {
            return List.of();
        }
        return catalogQueryService.getPublicDescendants(nodeId, null, true, List.of())
                .stream()
                .map(CatalogDescendantNodeResponse::id)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findLeafIds(String groupCode, Collection<UUID> refItemIds) {
        String normGroup = CatalogNormalizer.normalizeCode(groupCode);
        if (normGroup == null) {
            return List.of();
        }
        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normGroup).orElse(null);
        if (group == null
                || group.getKind() != CatalogGroupKind.TREE
                || group.getStatus() != CatalogStatus.ACTIVE) {
            return List.of();
        }
        return catalogQueryService.findActiveLeafIds(group, refItemIds);
    }

    private static CatalogItemView toView(
            CatalogGroup group, CatalogItem item, CatalogStatus effectiveStatus) {
        return new CatalogItemView(
                item.getId(),
                item.getGroupId(),
                group.getCode(),
                item.getCode(),
                item.getValue(),
                item.getNameVi(),
                item.getNameEn(),
                item.getStatus(),
                effectiveStatus,
                item.isSystem(),
                item.getParentId(),
                item.getDepth(),
                item.getPath());
    }
}
