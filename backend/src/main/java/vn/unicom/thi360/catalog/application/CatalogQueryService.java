package vn.unicom.thi360.catalog.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.catalog.api.CatalogDescendantNodeResponse;
import vn.unicom.thi360.catalog.api.CatalogInheritedRefResponse;
import vn.unicom.thi360.catalog.api.CatalogItemRefResponse;
import vn.unicom.thi360.catalog.api.CatalogItemResponse;
import vn.unicom.thi360.catalog.api.CatalogOptionResponse;
import vn.unicom.thi360.catalog.api.CatalogTreeNodeResponse;
import vn.unicom.thi360.catalog.domain.CatalogGroup;
import vn.unicom.thi360.catalog.domain.CatalogItem;
import vn.unicom.thi360.catalog.domain.CatalogNormalizer;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;

@Service
public class CatalogQueryService {

    private static final String DEFAULT_TREE_GROUP = "knowledge";

    private final CatalogGroupRepository catalogGroupRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final CatalogCacheService catalogCacheService;
    private final CatalogSupportDao catalogSupportDao;
    private final JdbcTemplate jdbcTemplate;

    public CatalogQueryService(
            CatalogGroupRepository catalogGroupRepository,
            CatalogItemRepository catalogItemRepository,
            CatalogCacheService catalogCacheService,
            CatalogSupportDao catalogSupportDao,
            JdbcTemplate jdbcTemplate) {
        this.catalogGroupRepository = catalogGroupRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogCacheService = catalogCacheService;
        this.catalogSupportDao = catalogSupportDao;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CatalogOptionResponse> getOptions(String groupCode) {
        String normalized = CatalogNormalizer.normalizeCode(groupCode);
        if (normalized == null) {
            return List.of();
        }
        List<CatalogOptionResponse> cached = catalogCacheService.getOptions(normalized);
        if (cached != null) {
            return cached;
        }
        Long generation = catalogCacheService.readGeneration(normalized);
        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normalized).orElse(null);
        if (group == null || group.getStatus() != CatalogStatus.ACTIVE) {
            return List.of();
        }
        String sql =
                """
                SELECT n.id, n.code, n.value, n.name_vi, n.name_en, n.description, n.display_order
                FROM catalog.catalog_item n
                WHERE n.group_id = ?
                  AND\s"""
                        + CatalogSupportDao.ACTIVE_EFFECTIVE_ITEM_PREDICATE
                        + " ORDER BY n.display_order ASC, n.code_normalized ASC, n.id ASC";
        List<CatalogOptionResponse> options = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CatalogOptionResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getInt("value"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        rs.getString("description"),
                        rs.getInt("display_order")),
                group.getId());
        if (group.getCodeNormalized().equals(normalized)) {
            catalogCacheService.putOptions(normalized, generation, options);
        }
        return options;
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse getPublicItem(UUID itemId) {
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        Set<UUID> inactiveEffective = catalogSupportDao.findInactiveEffectiveItemIds(
                group.getId(), List.of(item.getId()));
        CatalogStatus effectiveStatus = inactiveEffective.contains(item.getId())
                ? CatalogStatus.INACTIVE
                : CatalogStatus.ACTIVE;

        Map<UUID, Long> childCounts = catalogSupportDao.countDirectChildren(List.of(item.getId()));
        Map<UUID, List<CatalogItemRefResponse>> ownRefs = catalogSupportDao.loadOwnRefs(List.of(item.getId()));
        Map<UUID, List<CatalogInheritedRefResponse>> inheritedRefs =
                catalogSupportDao.loadInheritedRefs(group.getId(), List.of(item.getId()));

        return new CatalogItemResponse(
                item.getId(),
                item.getGroupId(),
                group.getCode(),
                item.getCode(),
                item.getValue(),
                item.getNameVi(),
                item.getNameEn(),
                item.getDescription(),
                item.getDisplayOrder(),
                item.getStatus(),
                effectiveStatus,
                item.isSystem(),
                item.getParentId(),
                item.getDepth(),
                item.getPath(),
                childCounts.getOrDefault(item.getId(), 0L),
                ownRefs.getOrDefault(item.getId(), List.of()),
                inheritedRefs.getOrDefault(item.getId(), List.of()),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<CatalogTreeNodeResponse> getPublicTree(
            String groupCode, UUID parentId, List<UUID> refItemIds) {
        String normalized = CatalogNormalizer.normalizeCode(groupCode);
        if (normalized == null) {
            return List.of();
        }
        boolean hasRefFilter = refItemIds != null && !refItemIds.isEmpty();
        String cacheField = parentId == null ? "root" : parentId.toString();
        Long generation = null;
        if (!hasRefFilter) {
            List<CatalogTreeNodeResponse> cached = catalogCacheService.getTreeNodes(normalized, cacheField);
            if (cached != null) {
                return cached;
            }
            generation = catalogCacheService.readGeneration(normalized);
        }

        CatalogGroup group = catalogGroupRepository.findByCodeNormalized(normalized).orElse(null);
        if (group == null
                || group.getStatus() != CatalogStatus.ACTIVE
                || group.getKind() != CatalogGroupKind.TREE) {
            return List.of();
        }

        if (parentId != null) {
            CatalogItem parent = catalogItemRepository.findById(parentId).orElse(null);
            if (parent == null || !parent.getGroupId().equals(group.getId())) {
                return List.of();
            }
            Set<UUID> inactiveParent =
                    catalogSupportDao.findInactiveEffectiveItemIds(group.getId(), List.of(parentId));
            if (inactiveParent.contains(parentId)) {
                return List.of();
            }
        }

        CatalogSupportDao.RefFilterCriteria refFilter =
                catalogSupportDao.resolveRefFilter(group.getId(), refItemIds);
        if (refFilter.impossible()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder(
                """
                SELECT n.id, n.group_id, n.code, n.value, n.name_vi, n.name_en, n.description,
                       n.display_order, n.status, n.parent_id, n.depth, n.path, n.version
                FROM catalog.catalog_item n
                WHERE n.group_id = ?
                  AND n.status = 1
                """);
        List<Object> params = new ArrayList<>();
        params.add(group.getId());

        if (parentId == null) {
            sql.append(" AND n.parent_id IS NULL");
        } else {
            sql.append(" AND n.parent_id = ?");
            params.add(parentId);
        }

        if (hasRefFilter) {
            String refPlaceholders =
                    String.join(",", Collections.nCopies(refFilter.refItemIds().size(), "?"));
            sql.append(
                    """
                     AND EXISTS (
                         SELECT 1
                         FROM catalog.catalog_item cand
                         WHERE cand.group_id = n.group_id
                           AND cand.path LIKE n.path || '%%'
                           AND NOT EXISTS (
                               SELECT 1
                               FROM catalog.catalog_item anc
                               WHERE anc.group_id = cand.group_id
                                 AND cand.path LIKE anc.path || '%%'
                                 AND anc.status <> 1
                           )
                           AND (
                               SELECT COUNT(DISTINCT ri.group_id)
                               FROM catalog.catalog_item anc2
                               JOIN catalog.catalog_item_ref cir ON cir.item_id = anc2.id
                               JOIN catalog.catalog_item ri ON ri.id = cir.ref_item_id
                               WHERE anc2.group_id = cand.group_id
                                 AND cand.path LIKE anc2.path || '%%'
                                 AND cir.ref_item_id IN (%s)
                           ) = ?
                     )
                    """.formatted(refPlaceholders));
            params.addAll(refFilter.refItemIds());
            params.add(refFilter.distinctGroupCount());
        }

        sql.append(" ORDER BY n.display_order ASC, n.code_normalized ASC, n.id ASC");

        List<TreeRow> rows = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new TreeRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("group_id", UUID.class),
                        rs.getString("code"),
                        rs.getInt("value"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        rs.getString("description"),
                        rs.getInt("display_order"),
                        CatalogStatus.fromCode(rs.getShort("status")),
                        rs.getObject("parent_id", UUID.class),
                        rs.getShort("depth"),
                        rs.getString("path"),
                        rs.getLong("version")),
                params.toArray());

        if (rows.isEmpty()) {
            if (!hasRefFilter && group.getCodeNormalized().equals(normalized)) {
                catalogCacheService.putTreeNodes(normalized, cacheField, generation, List.of());
            }
            return List.of();
        }

        List<UUID> itemIds = rows.stream().map(TreeRow::id).toList();
        Map<UUID, Long> childCounts = catalogSupportDao.countActiveDirectChildren(itemIds);
        Map<UUID, List<CatalogItemRefResponse>> ownRefs = catalogSupportDao.loadOwnRefs(itemIds);
        Map<UUID, List<CatalogInheritedRefResponse>> inheritedRefs =
                catalogSupportDao.loadInheritedRefs(group.getId(), itemIds);

        List<CatalogTreeNodeResponse> result = rows.stream()
                .map(r -> {
                    long children = childCounts.getOrDefault(r.id(), 0L);
                    return new CatalogTreeNodeResponse(
                            r.id(),
                            r.groupId(),
                            r.code(),
                            r.value(),
                            r.nameVi(),
                            r.nameEn(),
                            r.description(),
                            r.displayOrder(),
                            r.status(),
                            CatalogStatus.ACTIVE,
                            r.parentId(),
                            r.depth(),
                            r.path(),
                            children,
                            children > 0,
                            false,
                            ownRefs.getOrDefault(r.id(), List.of()),
                            inheritedRefs.getOrDefault(r.id(), List.of()),
                            r.version());
                })
                .toList();

        if (!hasRefFilter && group.getCodeNormalized().equals(normalized)) {
            catalogCacheService.putTreeNodes(normalized, cacheField, generation, result);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CatalogDescendantNodeResponse> getPublicDescendants(
            UUID nodeId, String groupCode, boolean leafOnly, List<UUID> refItemIds) {
        boolean hasRefFilter = refItemIds != null && !refItemIds.isEmpty();
        if (nodeId == null && !hasRefFilter) {
            throw new ValidationException(List.of(
                    new FieldError("nodeId", "REQUIRED", "Phải truyền nodeId hoặc tham số lọc ref")));
        }

        String cacheField = nodeId != null ? "desc:" + nodeId + ":" + leafOnly : null;
        String initialGroupCode = null;
        Long generation = null;
        if (!hasRefFilter && cacheField != null) {
            initialGroupCode = catalogItemRepository.findGroupCodeNormalizedById(nodeId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            List<CatalogDescendantNodeResponse> cached =
                    catalogCacheService.getDescendants(initialGroupCode, cacheField);
            if (cached != null) {
                return cached;
            }
            generation = catalogCacheService.readGeneration(initialGroupCode);
        }

        CatalogGroup group;
        CatalogItem rootNode = null;
        if (nodeId != null) {
            rootNode = catalogItemRepository.findById(nodeId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            group = catalogGroupRepository.findById(rootNode.getGroupId())
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            if (group.getKind() != CatalogGroupKind.TREE) {
                throw new ValidationException(List.of(
                        new FieldError("nodeId", "NOT_TREE", "Nhóm của nút này không phải danh mục cây")));
            }
            if (group.getStatus() != CatalogStatus.ACTIVE) {
                return List.of();
            }
            Set<UUID> inactiveRoot =
                    catalogSupportDao.findInactiveEffectiveItemIds(group.getId(), List.of(rootNode.getId()));
            if (inactiveRoot.contains(rootNode.getId())) {
                return List.of();
            }
        } else {
            String normalized = CatalogNormalizer.normalizeCode(groupCode);
            String targetCode = normalized != null ? normalized : DEFAULT_TREE_GROUP;
            group = catalogGroupRepository.findByCodeNormalized(targetCode).orElse(null);
            if (group == null
                    || group.getStatus() != CatalogStatus.ACTIVE
                    || group.getKind() != CatalogGroupKind.TREE) {
                return List.of();
            }
        }

        List<CatalogDescendantNodeResponse> result =
                queryDescendantsInternal(group, rootNode, leafOnly, refItemIds);

        if (!hasRefFilter && cacheField != null && group.getCodeNormalized().equals(initialGroupCode)) {
            catalogCacheService.putDescendants(group.getCodeNormalized(), cacheField, generation, result);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<UUID> findActiveLeafIds(CatalogGroup group, Collection<UUID> refItemIds) {
        if (group == null
                || group.getStatus() != CatalogStatus.ACTIVE
                || group.getKind() != CatalogGroupKind.TREE) {
            return List.of();
        }
        List<UUID> refs = refItemIds == null ? List.of() : new ArrayList<>(refItemIds);
        return queryDescendantsInternal(group, null, true, refs)
                .stream()
                .map(CatalogDescendantNodeResponse::id)
                .toList();
    }

    private List<CatalogDescendantNodeResponse> queryDescendantsInternal(
            CatalogGroup group, CatalogItem rootNode, boolean leafOnly, List<UUID> refItemIds) {
        boolean hasRefFilter = refItemIds != null && !refItemIds.isEmpty();
        if (hasRefFilter) {
            CatalogSupportDao.RefFilterCriteria refFilter =
                    catalogSupportDao.resolveRefFilter(group.getId(), refItemIds);
            if (refFilter.impossible()) {
                return List.of();
            }
            return executeDescendantsQuery(group, rootNode, leafOnly, refFilter);
        }
        return executeDescendantsQuery(group, rootNode, leafOnly, null);
    }

    private List<CatalogDescendantNodeResponse> executeDescendantsQuery(
            CatalogGroup group,
            CatalogItem rootNode,
            boolean leafOnly,
            CatalogSupportDao.RefFilterCriteria refFilter) {
        int leafDepth = group.getLevels() != null ? group.getLevels().size() : 1;
        StringBuilder sql = new StringBuilder(
                """
                SELECT n.id, n.code, n.value, n.name_vi, n.name_en, n.parent_id, n.depth, n.path
                FROM catalog.catalog_item n
                WHERE n.group_id = ?
                  AND\s"""
                        + CatalogSupportDao.ACTIVE_EFFECTIVE_ITEM_PREDICATE);
        List<Object> params = new ArrayList<>();
        params.add(group.getId());

        if (rootNode != null) {
            sql.append(" AND n.path LIKE ?");
            params.add(rootNode.getPath() + "%");
            if (!leafOnly) {
                sql.append(" AND n.id <> ?");
                params.add(rootNode.getId());
            }
        }
        if (leafOnly) {
            sql.append(" AND n.depth = ?");
            params.add((short) leafDepth);
        }

        if (refFilter != null) {
            String refPlaceholders =
                    String.join(",", Collections.nCopies(refFilter.refItemIds().size(), "?"));
            sql.append(
                    """
                     AND (
                         SELECT COUNT(DISTINCT ri.group_id)
                         FROM catalog.catalog_item anc2
                         JOIN catalog.catalog_item_ref cir ON cir.item_id = anc2.id
                         JOIN catalog.catalog_item ri ON ri.id = cir.ref_item_id
                         WHERE anc2.group_id = n.group_id
                           AND n.path LIKE anc2.path || '%%'
                           AND cir.ref_item_id IN (%s)
                     ) = ?
                    """.formatted(refPlaceholders));
            params.addAll(refFilter.refItemIds());
            params.add(refFilter.distinctGroupCount());
        }

        sql.append(" ORDER BY n.depth ASC, n.display_order ASC, n.code_normalized ASC, n.id ASC");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new CatalogDescendantNodeResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getInt("value"),
                        rs.getString("name_vi"),
                        rs.getString("name_en"),
                        rs.getObject("parent_id", UUID.class),
                        rs.getShort("depth"),
                        rs.getString("path")),
                params.toArray());
    }

    private record TreeRow(
            UUID id,
            UUID groupId,
            String code,
            int value,
            String nameVi,
            String nameEn,
            String description,
            int displayOrder,
            CatalogStatus status,
            UUID parentId,
            short depth,
            String path,
            long version
    ) {
    }
}
