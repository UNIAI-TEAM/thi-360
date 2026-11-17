package vn.unicom.thi360.catalog.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.api.CatalogDescendantNodeResponse;
import vn.unicom.thi360.catalog.api.CatalogInheritedRefResponse;
import vn.unicom.thi360.catalog.api.CatalogItemRefResponse;
import vn.unicom.thi360.catalog.api.CombinationSubjectResponse;
import vn.unicom.thi360.catalog.api.CombinationVersionResponse;
import vn.unicom.thi360.catalog.domain.CatalogGroup;
import vn.unicom.thi360.catalog.domain.CatalogItem;
import vn.unicom.thi360.catalog.domain.CatalogNormalizer;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.catalog.spi.CatalogCombinationSubjectView;
import vn.unicom.thi360.catalog.spi.CatalogCombinationView;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogItemRefView;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
@Service
public class CatalogLookupImpl implements CatalogLookup {

    private final CatalogGroupRepository catalogGroupRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final CatalogSupportDao catalogSupportDao;
    private final CatalogQueryService catalogQueryService;
    private final JdbcTemplate jdbcTemplate;

    public CatalogLookupImpl(
            CatalogGroupRepository catalogGroupRepository,
            CatalogItemRepository catalogItemRepository,
            CatalogSupportDao catalogSupportDao,
            CatalogQueryService catalogQueryService,
            JdbcTemplate jdbcTemplate) {
        this.catalogGroupRepository = catalogGroupRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogSupportDao = catalogSupportDao;
        this.catalogQueryService = catalogQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public long revision() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT revision FROM catalog.catalog_revision WHERE id = 1", Long.class);
        return value == null ? 0L : value;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItemView> listActiveGroupItems(String groupCode) {
        String normGroup = CatalogNormalizer.normalizeCode(groupCode);
        if (normGroup == null) {
            return List.of();
        }
        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normGroup).orElse(null);
        if (group == null || group.getStatus() != CatalogStatus.ACTIVE) {
            return List.of();
        }
        String sql =
                """
                SELECT n.id, n.code, n.code_normalized, n.value, n.name_vi, n.name_en, n.status,
                       n.is_system, n.parent_id, n.depth, n.path
                FROM catalog.catalog_item n
                WHERE n.group_id = ?
                  AND\s"""
                        + CatalogSupportDao.ACTIVE_EFFECTIVE_ITEM_PREDICATE
                        + " ORDER BY n.display_order ASC, n.code_normalized ASC, n.id ASC";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CatalogItemView(
                        rs.getObject("id", UUID.class),
                        group.getId(),
                        group.getCode(),
                        rs.getString("code"),
                        rs.getString("code_normalized"),
                        rs.getInt("value"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        CatalogStatus.fromCode(rs.getShort("status")),
                        CatalogStatus.ACTIVE,
                        rs.getBoolean("is_system"),
                        rs.getObject("parent_id", UUID.class),
                        rs.getShort("depth"),
                        rs.getString("path")),
                group.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogItemView> listGroupItems(String groupCode) {
        String normGroup = CatalogNormalizer.normalizeCode(groupCode);
        if (normGroup == null) {
            return List.of();
        }
        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normGroup).orElse(null);
        if (group == null || group.getStatus() != CatalogStatus.ACTIVE) {
            return List.of();
        }
        String sql =
                """
                SELECT n.id, n.code, n.code_normalized, n.value, n.name_vi, n.name_en, n.status,
                       n.is_system, n.parent_id, n.depth, n.path,
                       CASE WHEN n.status = 1 AND NOT EXISTS (
                           SELECT 1 FROM catalog.catalog_item anc
                           WHERE anc.group_id = n.group_id
                             AND n.path LIKE anc.path || '%'
                             AND anc.status <> 1
                       ) THEN 1 ELSE 2 END AS effective_status
                FROM catalog.catalog_item n
                WHERE n.group_id = ?
                ORDER BY n.display_order ASC, n.code_normalized ASC, n.id ASC""";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CatalogItemView(
                        rs.getObject("id", UUID.class),
                        group.getId(),
                        group.getCode(),
                        rs.getString("code"),
                        rs.getString("code_normalized"),
                        rs.getInt("value"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        CatalogStatus.fromCode(rs.getShort("status")),
                        CatalogStatus.fromCode(rs.getShort("effective_status")),
                        rs.getBoolean("is_system"),
                        rs.getObject("parent_id", UUID.class),
                        rs.getShort("depth"),
                        rs.getString("path")),
                group.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<CatalogItemRefView>> findEffectiveItemRefs(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(itemIds));
        Map<UUID, List<CatalogItemRefResponse>> own = catalogSupportDao.loadOwnRefs(ids);
        UUID groupId = catalogItemRepository.findGroupIdById(ids.getFirst()).orElse(null);
        Map<UUID, List<CatalogInheritedRefResponse>> inherited =
                groupId == null ? Map.of() : catalogSupportDao.loadInheritedRefs(groupId, ids);

        Map<UUID, List<CatalogItemRefView>> result = new LinkedHashMap<>();
        for (UUID itemId : ids) {
            LinkedHashMap<UUID, CatalogItemRefView> merged = new LinkedHashMap<>();
            for (CatalogItemRefResponse ref : own.getOrDefault(itemId, List.of())) {
                merged.put(ref.refItemId(), toRefView(ref));
            }
            for (CatalogInheritedRefResponse ref : inherited.getOrDefault(itemId, List.of())) {
                merged.putIfAbsent(ref.refItemId(), toRefView(ref));
            }
            if (!merged.isEmpty()) {
                result.put(itemId, List.copyOf(merged.values()));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogCombinationView> listCurrentCombinations(String groupCode) {
        String normGroup = CatalogNormalizer.normalizeCode(groupCode);
        if (normGroup == null) {
            return List.of();
        }
        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normGroup).orElse(null);
        if (group == null
                || group.getStatus() != CatalogStatus.ACTIVE
                || group.getKind() != CatalogGroupKind.COMBINATION) {
            return List.of();
        }
        List<CatalogItemView> items = listActiveGroupItems(normGroup);
        if (items.isEmpty()) {
            return List.of();
        }
        Map<UUID, CombinationVersionResponse> byItem = catalogSupportDao.loadCurrentCombinations(
                items.stream().map(CatalogItemView::id).toList());
        List<CatalogCombinationView> result = new ArrayList<>(items.size());
        for (CatalogItemView item : items) {
            CombinationVersionResponse combination = byItem.get(item.id());
            if (combination == null || combination.examStatus() != CatalogStatus.ACTIVE) {
                continue;
            }
            result.add(new CatalogCombinationView(
                    item.id(),
                    item.code(),
                    item.nameVi(),
                    item.nameEn(),
                    combination.examItemId(),
                    combination.examCode(),
                    combination.examNameVi(),
                    combination.examNameEn(),
                    combination.branch(),
                    combination.versionNo(),
                    combination.subjects().stream()
                            .filter(s -> s.status() == CatalogStatus.ACTIVE)
                            .sorted(java.util.Comparator.comparingInt(CombinationSubjectResponse::position))
                            .map(s -> new CatalogCombinationSubjectView(
                                    s.subjectItemId(), s.code(), s.nameVi(), s.nameEn()))
                            .toList()));
        }
        return result;
    }
    @Override
    @Transactional(readOnly = true)
    public List<CatalogCombinationView> listCombinations(UUID examItemId) {
        if (examItemId == null) {
            return List.of();
        }
        return listCurrentCombinations("SUBJECT_COMBINATION").stream()
                .filter(combo -> examItemId.equals(combo.examItemId()))
                .toList();
    }

    private static CatalogItemRefView toRefView(CatalogItemRefResponse ref) {
        return new CatalogItemRefView(
                ref.refItemId(),
                ref.refGroupCode(),
                ref.code(),
                ref.nameVi(),
                ref.nameEn(),
                ref.status());
    }

    private static CatalogItemRefView toRefView(CatalogInheritedRefResponse ref) {
        return new CatalogItemRefView(
                ref.refItemId(),
                ref.refGroupCode(),
                ref.code(),
                ref.nameVi(),
                ref.nameEn(),
                ref.status());
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
                item.getCodeNormalized(),
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
