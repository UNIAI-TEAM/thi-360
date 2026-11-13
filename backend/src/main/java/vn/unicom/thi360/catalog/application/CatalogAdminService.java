package vn.unicom.thi360.catalog.application;

import jakarta.persistence.EntityManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.api.CatalogGroupRefResponse;
import vn.unicom.thi360.catalog.api.CatalogGroupResponse;
import vn.unicom.thi360.catalog.api.CatalogInheritedRefResponse;
import vn.unicom.thi360.catalog.api.CatalogItemRefResponse;
import vn.unicom.thi360.catalog.api.CatalogItemResponse;
import vn.unicom.thi360.catalog.api.CatalogTreeNodeResponse;
import vn.unicom.thi360.catalog.api.CreateCatalogGroupRequest;
import vn.unicom.thi360.catalog.api.CreateCatalogItemRequest;
import vn.unicom.thi360.catalog.api.MoveCatalogItemRequest;
import vn.unicom.thi360.catalog.api.ReorderCatalogItemsRequest;
import vn.unicom.thi360.catalog.api.UpdateCatalogGroupRequest;
import vn.unicom.thi360.catalog.api.UpdateCatalogItemRequest;
import vn.unicom.thi360.catalog.domain.CatalogGroup;
import vn.unicom.thi360.catalog.domain.CatalogGroupRef;
import vn.unicom.thi360.catalog.domain.CatalogItem;
import vn.unicom.thi360.catalog.domain.CatalogItemRef;
import vn.unicom.thi360.catalog.domain.CatalogItemRefId;
import vn.unicom.thi360.catalog.domain.CatalogNormalizer;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRefRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRefRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogGroupType;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.security.Realm;

@Service
public class CatalogAdminService {

    public static final Set<String> ALLOWED_GROUP_SORT_FIELDS =
            Set.of("code", "nameVi", "nameEn", "status", "type", "kind", "createdAt", "updatedAt");
    public static final Set<String> ALLOWED_ITEM_SORT_FIELDS =
            Set.of("displayOrder", "code", "value", "nameVi", "nameEn", "status", "createdAt", "updatedAt");

    private static final String MODULE = "CATALOG";
    private static final long MAX_TREE_NODES = 50_000L;

    private static final String ERR_CODE_TAKEN = "CATALOG_CODE_TAKEN";
    private static final String ERR_GROUP_INACTIVE = "CATALOG_GROUP_INACTIVE";
    private static final String ERR_GROUP_SYSTEM = "CATALOG_GROUP_SYSTEM";
    private static final String ERR_GROUP_NOT_EMPTY = "CATALOG_GROUP_NOT_EMPTY";
    private static final String ERR_ITEM_SYSTEM = "CATALOG_ITEM_SYSTEM";
    private static final String ERR_TREE_DEPTH_EXCEEDED = "CATALOG_TREE_DEPTH_EXCEEDED";
    private static final String ERR_PARENT_INVALID = "CATALOG_PARENT_INVALID";
    private static final String ERR_PARENT_INACTIVE = "CATALOG_PARENT_INACTIVE";
    private static final String ERR_NODE_HAS_CHILDREN = "CATALOG_NODE_HAS_CHILDREN";
    private static final String ERR_LEVELS_LOCKED = "CATALOG_LEVELS_LOCKED";
    private static final String ERR_TREE_TOO_LARGE = "CATALOG_TREE_TOO_LARGE";
    private static final String ERR_REF_GROUP_IN_USE = "CATALOG_REF_GROUP_IN_USE";
    private static final String ERR_REF_GROUP_DEFAULT = "CATALOG_REF_GROUP_DEFAULT";
    private static final String ERR_REF_INVALID = "CATALOG_REF_INVALID";

    private final CatalogGroupRepository catalogGroupRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final CatalogGroupRefRepository catalogGroupRefRepository;
    private final CatalogItemRefRepository catalogItemRefRepository;
    private final CatalogSupportDao catalogSupportDao;
    private final CatalogCacheService catalogCacheService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final Clock clock;

    public CatalogAdminService(
            CatalogGroupRepository catalogGroupRepository,
            CatalogItemRepository catalogItemRepository,
            CatalogGroupRefRepository catalogGroupRefRepository,
            CatalogItemRefRepository catalogItemRefRepository,
            CatalogSupportDao catalogSupportDao,
            CatalogCacheService catalogCacheService,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager,
            Clock clock) {
        this.catalogGroupRepository = catalogGroupRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.catalogGroupRefRepository = catalogGroupRefRepository;
        this.catalogItemRefRepository = catalogItemRefRepository;
        this.catalogSupportDao = catalogSupportDao;
        this.catalogCacheService = catalogCacheService;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogGroupResponse> listGroups(
            String q,
            CatalogStatus status,
            CatalogGroupType type,
            CatalogGroupKind kind,
            PageQuery pageQuery) {
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();

        if (status != null) {
            where.append(" AND g.status = ?");
            params.add(status.code());
        }
        if (type != null) {
            where.append(" AND g.type = ?");
            params.add(type.code());
        }
        if (kind != null) {
            where.append(" AND g.kind = ?");
            params.add(kind.code());
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(
                    " AND (LOWER(g.code) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(g.name_vi) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(g.name_en, '')) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(g.description, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        String countSql = "SELECT COUNT(*) FROM catalog.catalog_group g WHERE " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        String orderBy = buildGroupOrderBy(pageQuery.sort());
        String dataSql =
                """
                SELECT g.id,
                       (SELECT COUNT(*) FROM catalog.catalog_item ci WHERE ci.group_id = g.id) AS item_count
                FROM catalog.catalog_group g
                WHERE\s"""
                        + where
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());

        List<UUID> orderedIds = new ArrayList<>();
        Map<UUID, Long> itemCounts = new HashMap<>();
        jdbcTemplate.query(
                dataSql,
                rs -> {
                    UUID gid = rs.getObject("id", UUID.class);
                    orderedIds.add(gid);
                    itemCounts.put(gid, rs.getLong("item_count"));
                },
                dataParams.toArray());

        Map<UUID, CatalogGroup> groupsById = new HashMap<>();
        for (CatalogGroup group : catalogGroupRepository.findAllById(orderedIds)) {
            groupsById.put(group.getId(), group);
        }
        Map<UUID, List<CatalogGroupRefResponse>> refsByGroup =
                catalogSupportDao.loadGroupRefs(orderedIds);

        List<CatalogGroupResponse> items = new ArrayList<>(orderedIds.size());
        for (UUID gid : orderedIds) {
            CatalogGroup group = groupsById.get(gid);
            if (group != null) {
                items.add(toGroupResponse(
                        group,
                        itemCounts.getOrDefault(gid, 0L),
                        refsByGroup.getOrDefault(gid, List.of())));
            }
        }
        return PageResponse.from(new PageImpl<>(items, pageQuery.toPageable(), totalCount));
    }

    @Transactional(readOnly = true)
    public CatalogGroupResponse getGroup(UUID groupId) {
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        long itemCount = catalogItemRepository.countByGroupId(groupId);
        List<CatalogGroupRefResponse> refs =
                catalogSupportDao.loadGroupRefs(List.of(groupId)).getOrDefault(groupId, List.of());
        return toGroupResponse(group, itemCount, refs);
    }

    @Transactional
    public CatalogGroupResponse createGroup(CreateCatalogGroupRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String code = validateAndTrimCode(request.code());
        String codeNormalized = CatalogNormalizer.normalizeCode(code);
        if (catalogGroupRepository.existsByCodeNormalized(codeNormalized)) {
            throw new ConflictException(ERR_CODE_TAKEN);
        }

        CatalogGroupKind kind = request.kind() == null ? CatalogGroupKind.SIMPLE : request.kind();
        if (kind == CatalogGroupKind.COMBINATION) {
            throw new ValidationException(List.of(
                    new FieldError("kind", "INVALID_KIND", "Không thể tạo nhóm kiểu COMBINATION")));
        }

        List<String> validatedLevels = null;
        if (kind == CatalogGroupKind.TREE) {
            validatedLevels = validateLevels(request.levels());
        } else if (request.levels() != null && !request.levels().isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("levels", "INVALID_FOR_SIMPLE", "Chỉ nhóm TREE mới có danh sách cấp")));
        }

        List<UUID> refGroupIds = deduplicateIds(request.refGroupIds());
        if (kind != CatalogGroupKind.TREE && !refGroupIds.isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("refGroupIds", "INVALID_FOR_SIMPLE", "Chỉ nhóm TREE mới có danh mục liên kết")));
        }
        if (kind == CatalogGroupKind.TREE && !refGroupIds.isEmpty()) {
            validateRefGroupsForAdd(null, refGroupIds);
        }

        CatalogStatus status = request.status() == null ? CatalogStatus.ACTIVE : request.status();
        Instant now = Instant.now(clock);
        CatalogGroup group = new CatalogGroup(
                Ids.newId(),
                code,
                request.nameVi(),
                request.nameEn(),
                request.description(),
                status,
                CatalogGroupType.CUSTOM,
                kind,
                validatedLevels,
                now,
                actor.id());
        group = catalogGroupRepository.saveAndFlush(group);

        if (kind == CatalogGroupKind.TREE && !refGroupIds.isEmpty()) {
            List<CatalogGroupRef> refs = new ArrayList<>(refGroupIds.size());
            for (int i = 0; i < refGroupIds.size(); i++) {
                refs.add(new CatalogGroupRef(group.getId(), refGroupIds.get(i), i, false));
            }
            catalogGroupRefRepository.saveAll(refs);
            catalogGroupRefRepository.flush();
        }

        List<CatalogGroupRefResponse> savedRefs =
                catalogSupportDao.loadGroupRefs(List.of(group.getId()))
                        .getOrDefault(group.getId(), List.of());

        catalogCacheService.evictGroupAndDependents(group.getId(), group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_GROUP_CREATED",
                "CATALOG_GROUP",
                group.getId(),
                group.getCode(),
                null,
                groupAuditState(group, savedRefs),
                null);

        return toGroupResponse(group, 0L, savedRefs);
    }

    @Transactional
    public CatalogGroupResponse updateGroup(UUID groupId, UpdateCatalogGroupRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (group.getType() == CatalogGroupType.SYSTEM || group.isSystem()) {
            throw new ConflictException(ERR_GROUP_SYSTEM);
        }
        if (!Objects.equals(group.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        List<CatalogGroupRefResponse> existingRefs =
                catalogSupportDao.loadGroupRefs(List.of(groupId)).getOrDefault(groupId, List.of());
        Map<String, Object> before = groupAuditState(group, existingRefs);
        String oldCodeNormalized = group.getCodeNormalized();

        String newCode = null;
        if (request.code() != null) {
            String trimmed = validateAndTrimCode(request.code());
            String normalized = CatalogNormalizer.normalizeCode(trimmed);
            if (!normalized.equals(group.getCodeNormalized())
                    && catalogGroupRepository.existsByCodeNormalizedAndIdNot(normalized, groupId)) {
                throw new ConflictException(ERR_CODE_TAKEN);
            }
            newCode = trimmed;
        }

        String newNameVi = null;
        if (request.nameVi() != null) {
            if (request.nameVi().isBlank()) {
                throw new ValidationException(List.of(
                        new FieldError("nameVi", "NOT_BLANK", "Tên tiếng Việt của nhóm không được để trống")));
            }
            newNameVi = request.nameVi();
        }

        List<String> newLevels = null;
        if (request.levels() != null) {
            if (group.getKind() != CatalogGroupKind.TREE) {
                throw new ValidationException(List.of(
                        new FieldError("levels", "INVALID_FOR_SIMPLE", "Chỉ nhóm TREE mới có danh sách cấp")));
            }
            newLevels = validateLevels(request.levels());
            int currentCount = group.getLevels() == null ? 0 : group.getLevels().size();
            if (newLevels.size() != currentCount && catalogItemRepository.existsByGroupId(groupId)) {
                throw new ConflictException(ERR_LEVELS_LOCKED);
            }
        }

        if (request.refGroupIds() != null) {
            if (group.getKind() != CatalogGroupKind.TREE) {
                throw new ValidationException(List.of(
                        new FieldError("refGroupIds", "INVALID_FOR_SIMPLE", "Chỉ nhóm TREE mới có danh mục liên kết")));
            }
            List<UUID> requestedRefIds = deduplicateIds(request.refGroupIds());
            replaceGroupRefs(groupId, requestedRefIds);
        }

        Instant now = Instant.now(clock);
        group.updateDetails(
                newCode,
                newNameVi,
                request.nameEn(),
                request.description(),
                request.status(),
                newLevels,
                now,
                actor.id());
        group = catalogGroupRepository.saveAndFlush(group);

        long itemCount = catalogItemRepository.countByGroupId(groupId);
        List<CatalogGroupRefResponse> updatedRefs =
                catalogSupportDao.loadGroupRefs(List.of(groupId)).getOrDefault(groupId, List.of());

        catalogCacheService.evictGroupAndDependents(
                group.getId(), oldCodeNormalized, group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_GROUP_UPDATED",
                "CATALOG_GROUP",
                group.getId(),
                group.getCode(),
                before,
                groupAuditState(group, updatedRefs),
                null);

        return toGroupResponse(group, itemCount, updatedRefs);
    }

    @Transactional
    public void deleteGroup(UUID groupId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (group.getType() == CatalogGroupType.SYSTEM || group.isSystem()) {
            throw new ConflictException(ERR_GROUP_SYSTEM);
        }
        if (catalogItemRepository.existsByGroupId(groupId)) {
            throw new ConflictException(ERR_GROUP_NOT_EMPTY);
        }
        if (catalogGroupRefRepository.existsByIdRefGroupId(groupId)) {
            throw new ConflictException(ERR_REF_GROUP_IN_USE);
        }

        List<CatalogGroupRefResponse> existingRefs =
                catalogSupportDao.loadGroupRefs(List.of(groupId)).getOrDefault(groupId, List.of());
        Map<String, Object> before = groupAuditState(group, existingRefs);
        String codeNormalized = group.getCodeNormalized();

        catalogGroupRefRepository.deleteAllByIdGroupId(groupId);
        catalogGroupRepository.delete(group);
        catalogGroupRepository.flush();

        catalogCacheService.evictGroupAndDependents(groupId, codeNormalized);
        recordAudit(
                actor,
                "CATALOG_GROUP_DELETED",
                "CATALOG_GROUP",
                groupId,
                group.getCode(),
                before,
                null,
                null);
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogItemResponse> listItems(
            UUID groupId,
            String nameVi,
            String nameEn,
            CatalogStatus status,
            String q,
            PageQuery pageQuery) {
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        StringBuilder where = new StringBuilder("i.group_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(groupId);

        if (status != null) {
            where.append(" AND i.status = ?");
            params.add(status.code());
        }
        if (nameVi != null && !nameVi.isBlank()) {
            where.append(" AND LOWER(i.name_vi) LIKE ? ESCAPE '\\'");
            params.add("%" + escapeLike(nameVi.trim().toLowerCase(Locale.ROOT)) + "%");
        }
        if (nameEn != null && !nameEn.isBlank()) {
            where.append(" AND LOWER(COALESCE(i.name_en, '')) LIKE ? ESCAPE '\\'");
            params.add("%" + escapeLike(nameEn.trim().toLowerCase(Locale.ROOT)) + "%");
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(
                    " AND (LOWER(i.code) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(i.name_vi) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(i.name_en, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        String countSql = "SELECT COUNT(*) FROM catalog.catalog_item i WHERE " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        String orderBy = buildItemOrderBy(pageQuery.sort());
        String dataSql =
                """
                SELECT i.id
                FROM catalog.catalog_item i
                WHERE\s"""
                        + where
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());

        List<UUID> orderedIds = jdbcTemplate.query(
                dataSql, (rs, rowNum) -> rs.getObject("id", UUID.class), dataParams.toArray());
        Map<UUID, CatalogItem> byId = new HashMap<>();
        for (CatalogItem item : catalogItemRepository.findAllById(orderedIds)) {
            byId.put(item.getId(), item);
        }

        List<CatalogItem> orderedItems = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            CatalogItem item = byId.get(id);
            if (item != null) {
                orderedItems.add(item);
            }
        }
        List<CatalogItemResponse> responses = toItemResponses(group, orderedItems);
        return PageResponse.from(new PageImpl<>(responses, pageQuery.toPageable(), totalCount));
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse getItem(UUID itemId) {
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        return toItemResponses(group, List.of(item)).getFirst();
    }

    @Transactional
    public CatalogItemResponse createItem(UUID groupId, CreateCatalogItemRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (group.getStatus() != CatalogStatus.ACTIVE) {
            throw new ConflictException(ERR_GROUP_INACTIVE);
        }

        String code = validateAndTrimCode(request.code());
        String codeNormalized = CatalogNormalizer.normalizeCode(code);
        if (catalogItemRepository.existsByGroupIdAndCodeNormalized(groupId, codeNormalized)) {
            throw new ConflictException(ERR_CODE_TAKEN);
        }

        UUID itemId = Ids.newId();
        UUID parentId = request.parentId();
        short depth = 1;
        String path = "/" + itemId + "/";

        if (group.getKind() != CatalogGroupKind.TREE) {
            if (parentId != null) {
                throw new ConflictException(ERR_PARENT_INVALID);
            }
        } else {
            if (catalogItemRepository.countByGroupId(groupId) >= MAX_TREE_NODES) {
                throw new ConflictException(ERR_TREE_TOO_LARGE);
            }
            if (parentId != null) {
                CatalogItem parent = catalogItemRepository.findById(parentId).orElse(null);
                if (parent == null || !parent.getGroupId().equals(groupId)) {
                    throw new ConflictException(ERR_PARENT_INVALID);
                }
                Set<UUID> inactiveParent =
                        catalogSupportDao.findInactiveEffectiveItemIds(groupId, List.of(parentId));
                if (parent.getStatus() != CatalogStatus.ACTIVE || inactiveParent.contains(parentId)) {
                    throw new ConflictException(ERR_PARENT_INACTIVE);
                }
                int maxDepth = group.getLevels() == null ? 1 : group.getLevels().size();
                if (parent.getDepth() >= maxDepth) {
                    throw new ConflictException(ERR_TREE_DEPTH_EXCEEDED);
                }
                depth = (short) (parent.getDepth() + 1);
                path = parent.getPath() + itemId + "/";
            }
        }

        List<UUID> refItemIds = deduplicateIds(request.refItemIds());
        if (!refItemIds.isEmpty()) {
            if (group.getKind() != CatalogGroupKind.TREE) {
                throw new ValidationException(
                        ERR_REF_INVALID,
                        null,
                        List.of(new FieldError("refItemIds", ERR_REF_INVALID, "Chỉ nhóm TREE mới gắn được liên kết")));
            }
            validateItemRefsForAdd(groupId, refItemIds, Set.of());
        }

        int maxExistingValue = catalogItemRepository.findMaxValueByGroupId(groupId);
        int nextValue = Math.max(group.getLastValue(), maxExistingValue) + 1;
        catalogGroupRepository.updateLastValue(groupId, nextValue);

        int displayOrder = request.displayOrder() != null
                ? request.displayOrder()
                : catalogItemRepository.findMaxDisplayOrder(groupId, parentId) + 1;
        CatalogStatus status = request.status() == null ? CatalogStatus.ACTIVE : request.status();

        Instant now = Instant.now(clock);
        CatalogItem item = new CatalogItem(
                itemId,
                groupId,
                code,
                nextValue,
                request.nameVi(),
                request.nameEn(),
                request.description(),
                displayOrder,
                status,
                false,
                parentId,
                depth,
                path,
                now,
                actor.id());
        item = catalogItemRepository.saveAndFlush(item);

        if (!refItemIds.isEmpty()) {
            List<CatalogItemRef> refs = new ArrayList<>(refItemIds.size());
            for (UUID refItemId : refItemIds) {
                refs.add(new CatalogItemRef(item.getId(), refItemId, actor.id(), now));
            }
            catalogItemRefRepository.saveAll(refs);
            catalogItemRefRepository.flush();
        }

        CatalogItemResponse response = toItemResponses(group, List.of(item)).getFirst();
        catalogCacheService.evictGroupAndDependents(groupId, group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_ITEM_CREATED",
                "CATALOG_ITEM",
                item.getId(),
                item.getCode(),
                null,
                itemAuditState(item, response.ownRefs()),
                null);

        return response;
    }

    @Transactional
    public CatalogItemResponse updateItem(UUID itemId, UpdateCatalogItemRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (!Objects.equals(item.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        List<CatalogItemRefResponse> beforeRefs =
                catalogSupportDao.loadOwnRefs(List.of(itemId)).getOrDefault(itemId, List.of());
        Map<String, Object> before = itemAuditState(item, beforeRefs);

        CatalogStatus previousStatus = item.getStatus();
        String newCode = null;
        if (request.code() != null) {
            String trimmed = validateAndTrimCode(request.code());
            if (!trimmed.equals(item.getCode()) && item.isSystem()) {
                throw new ConflictException(ERR_ITEM_SYSTEM);
            }
            String normalized = CatalogNormalizer.normalizeCode(trimmed);
            if (!normalized.equals(item.getCodeNormalized())
                    && catalogItemRepository.existsByGroupIdAndCodeNormalizedAndIdNot(
                            group.getId(), normalized, itemId)) {
                throw new ConflictException(ERR_CODE_TAKEN);
            }
            newCode = trimmed;
        }

        String newNameVi = null;
        if (request.nameVi() != null) {
            if (request.nameVi().isBlank()) {
                throw new ValidationException(List.of(
                        new FieldError("nameVi", "NOT_BLANK", "Tên tiếng Việt không được để trống")));
            }
            newNameVi = request.nameVi();
        }

        Instant now = Instant.now(clock);
        if (request.refItemIds() != null) {
            if (group.getKind() != CatalogGroupKind.TREE) {
                throw new ValidationException(
                        ERR_REF_INVALID,
                        null,
                        List.of(new FieldError("refItemIds", ERR_REF_INVALID, "Chỉ nhóm TREE mới gắn được liên kết")));
            }
            List<UUID> requestedRefIds = deduplicateIds(request.refItemIds());
            Set<UUID> existingOwnRefIds = new HashSet<>();
            for (CatalogItemRefResponse ref : beforeRefs) {
                existingOwnRefIds.add(ref.refItemId());
            }
            validateItemRefsForAdd(group.getId(), requestedRefIds, existingOwnRefIds);
            catalogItemRefRepository.deleteAllByIdItemId(itemId);
            catalogItemRefRepository.flush();
            if (!requestedRefIds.isEmpty()) {
                List<CatalogItemRef> newRefs = new ArrayList<>(requestedRefIds.size());
                for (UUID refItemId : requestedRefIds) {
                    newRefs.add(new CatalogItemRef(itemId, refItemId, actor.id(), now));
                }
                catalogItemRefRepository.saveAll(newRefs);
                catalogItemRefRepository.flush();
            }
        }

        item.updateDetails(
                newCode,
                newNameVi,
                request.nameEn(),
                request.description(),
                request.displayOrder(),
                request.status(),
                now,
                actor.id());
        item = catalogItemRepository.saveAndFlush(item);

        CatalogItemResponse response = toItemResponses(group, List.of(item)).getFirst();
        String action = previousStatus == CatalogStatus.ACTIVE && item.getStatus() == CatalogStatus.INACTIVE
                ? "CATALOG_ITEM_DEACTIVATED"
                : "CATALOG_ITEM_UPDATED";

        catalogCacheService.evictGroupAndDependents(group.getId(), group.getCodeNormalized());
        recordAudit(
                actor,
                action,
                "CATALOG_ITEM",
                item.getId(),
                item.getCode(),
                before,
                itemAuditState(item, response.ownRefs()),
                CatalogNormalizer.trimToNull(request.reason()));

        return response;
    }

    @Transactional
    public List<CatalogItemResponse> reorderItems(UUID groupId, ReorderCatalogItemsRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        List<UUID> requestedIds = request.itemIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("itemIds", "NOT_EMPTY", "Danh sách sắp xếp không được để trống")));
        }

        Set<UUID> seen = new LinkedHashSet<>();
        for (UUID id : requestedIds) {
            if (id == null) {
                throw new ValidationException(List.of(
                        new FieldError("itemIds", "INVALID", "Danh sách sắp xếp chứa ID trống")));
            }
            if (!seen.add(id)) {
                throw new ValidationException(List.of(
                        new FieldError("itemIds", "DUPLICATE", "Danh sách sắp xếp chứa ID trùng lặp")));
            }
        }

        List<CatalogItem> loaded = catalogItemRepository.findByGroupIdAndIdIn(groupId, seen);
        if (loaded.size() != seen.size()) {
            throw new ValidationException(List.of(
                    new FieldError("itemIds", "INVALID_ITEMS", "Một hoặc nhiều giá trị không thuộc nhóm này")));
        }

        Map<UUID, CatalogItem> byId = new HashMap<>();
        for (CatalogItem item : loaded) {
            byId.put(item.getId(), item);
        }

        UUID commonParentId = byId.get(requestedIds.getFirst()).getParentId();
        if (group.getKind() == CatalogGroupKind.TREE) {
            for (UUID id : requestedIds) {
                if (!Objects.equals(commonParentId, byId.get(id).getParentId())) {
                    throw new ConflictException(ERR_PARENT_INVALID);
                }
            }
        }

        Instant now = Instant.now(clock);
        List<CatalogItem> ordered = new ArrayList<>(requestedIds.size());
        for (int i = 0; i < requestedIds.size(); i++) {
            CatalogItem item = byId.get(requestedIds.get(i));
            item.updateDisplayOrder(i, now, actor.id());
            ordered.add(item);
        }

        List<UUID> remainingSiblingIds;
        if (commonParentId == null) {
            remainingSiblingIds = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM catalog.catalog_item
                    WHERE group_id = ? AND parent_id IS NULL
                    ORDER BY display_order ASC, code_normalized ASC, id ASC
                    """,
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    groupId);
        } else {
            remainingSiblingIds = jdbcTemplate.query(
                    """
                    SELECT id
                    FROM catalog.catalog_item
                    WHERE group_id = ? AND parent_id = ?
                    ORDER BY display_order ASC, code_normalized ASC, id ASC
                    """,
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    groupId,
                    commonParentId);
        }

        int nextOrder = requestedIds.size();
        List<UUID> extraIds = new ArrayList<>();
        for (UUID sibId : remainingSiblingIds) {
            if (!seen.contains(sibId)) {
                extraIds.add(sibId);
            }
        }
        if (!extraIds.isEmpty()) {
            Map<UUID, CatalogItem> extraMap = new HashMap<>();
            for (CatalogItem extra : catalogItemRepository.findAllById(extraIds)) {
                extraMap.put(extra.getId(), extra);
            }
            for (UUID extraId : extraIds) {
                CatalogItem extra = extraMap.get(extraId);
                if (extra != null) {
                    extra.updateDisplayOrder(nextOrder++, now, actor.id());
                }
            }
        }

        catalogItemRepository.flush();
        catalogCacheService.evictGroupAndDependents(groupId, group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_ITEM_REORDERED",
                "CATALOG_GROUP",
                groupId,
                group.getCode(),
                null,
                Map.of("itemIds", requestedIds, "parentId", commonParentId == null ? "" : commonParentId.toString()),
                null);

        return toItemResponses(group, ordered);
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (item.isSystem()) {
            throw new ConflictException(ERR_ITEM_SYSTEM);
        }
        if (catalogItemRepository.existsByParentId(itemId)) {
            throw new ConflictException(ERR_NODE_HAS_CHILDREN);
        }

        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        List<CatalogItemRefResponse> beforeRefs =
                catalogSupportDao.loadOwnRefs(List.of(itemId)).getOrDefault(itemId, List.of());
        Map<String, Object> before = itemAuditState(item, beforeRefs);

        catalogItemRefRepository.deleteAllByIdItemId(itemId);
        catalogItemRepository.delete(item);
        catalogItemRepository.flush();

        catalogCacheService.evictGroupAndDependents(group.getId(), group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_ITEM_DELETED",
                "CATALOG_ITEM",
                itemId,
                item.getCode(),
                before,
                null,
                null);
    }

    @Transactional(readOnly = true)
    public List<CatalogTreeNodeResponse> listAdminTree(
            UUID groupId,
            UUID parentId,
            String q,
            Short depth,
            CatalogStatus status,
            List<UUID> refItemIds) {
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (group.getKind() != CatalogGroupKind.TREE) {
            throw new ValidationException(List.of(
                    new FieldError("id", "NOT_TREE", "Nhóm danh mục này không phải kiểu TREE")));
        }

        boolean hasQuery = q != null && !q.isBlank();
        boolean hasRefFilter = refItemIds != null && !refItemIds.isEmpty();
        CatalogSupportDao.RefFilterCriteria refFilter =
                catalogSupportDao.resolveRefFilter(groupId, refItemIds);
        if (refFilter.impossible()) {
            return List.of();
        }

        List<TreeRow> rows;
        Set<UUID> matchedIds = new HashSet<>();
        if (hasQuery || hasRefFilter) {
            StringBuilder matchWhere = new StringBuilder("m.group_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(groupId);

            if (hasQuery) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                matchWhere.append(
                        " AND (LOWER(m.code) LIKE ? ESCAPE '\\'"
                                + " OR LOWER(m.name_vi) LIKE ? ESCAPE '\\'"
                                + " OR LOWER(COALESCE(m.name_en, '')) LIKE ? ESCAPE '\\')");
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
            if (depth != null) {
                matchWhere.append(" AND m.depth = ?");
                params.add(depth);
            }
            if (status != null) {
                matchWhere.append(" AND m.status = ?");
                params.add(status.code());
            }
            if (hasRefFilter) {
                String refPlaceholders =
                        String.join(",", Collections.nCopies(refFilter.refItemIds().size(), "?"));
                matchWhere.append(
                        """
                         AND (
                             SELECT COUNT(DISTINCT ri.group_id)
                             FROM catalog.catalog_item anc2
                             JOIN catalog.catalog_item_ref cir ON cir.item_id = anc2.id
                             JOIN catalog.catalog_item ri ON ri.id = cir.ref_item_id
                             WHERE anc2.group_id = m.group_id
                               AND m.path LIKE anc2.path || '%%'
                               AND cir.ref_item_id IN (%s)
                         ) = ?
                        """.formatted(refPlaceholders));
                params.addAll(refFilter.refItemIds());
                params.add(refFilter.distinctGroupCount());
            }

            String sql =
                    """
                    SELECT anc.id, anc.group_id, anc.code, anc.code_normalized, anc.value, anc.name_vi, anc.name_en,
                           anc.description, anc.display_order, anc.status, anc.parent_id, anc.depth,
                           anc.path, anc.version,
                           BOOL_OR(anc.id = m.id) AS is_direct_match
                    FROM catalog.catalog_item anc
                    JOIN catalog.catalog_item m
                      ON m.group_id = anc.group_id
                     AND m.path LIKE anc.path || '%'
                    WHERE\s"""
                            + matchWhere
                            + " GROUP BY anc.id, anc.group_id, anc.code, anc.code_normalized, anc.value, anc.name_vi,"
                            + " anc.name_en, anc.description, anc.display_order, anc.status, anc.parent_id,"
                            + " anc.depth, anc.path, anc.version"
                            + " ORDER BY anc.depth ASC, anc.display_order ASC, anc.code_normalized ASC, anc.id ASC";
            Map<UUID, TreeRow> dedup = new LinkedHashMap<>();
            jdbcTemplate.query(
                    sql,
                    rs -> {
                        UUID id = rs.getObject("id", UUID.class);
                        if (rs.getBoolean("is_direct_match")) {
                            matchedIds.add(id);
                        }
                        dedup.putIfAbsent(id, mapTreeRow(rs));
                    },
                    params.toArray());
            rows = new ArrayList<>(dedup.values());
        } else {
            StringBuilder where = new StringBuilder("n.group_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(groupId);
            if (parentId == null && depth == null) {
                where.append(" AND n.parent_id IS NULL");
            } else if (parentId != null) {
                where.append(" AND n.parent_id = ?");
                params.add(parentId);
            }
            if (depth != null) {
                where.append(" AND n.depth = ?");
                params.add(depth);
            }
            if (status != null) {
                where.append(" AND n.status = ?");
                params.add(status.code());
            }
            String sql =
                    """
                    SELECT n.id, n.group_id, n.code, n.value, n.name_vi, n.name_en,
                           n.description, n.display_order, n.status, n.parent_id, n.depth,
                           n.path, n.version
                    FROM catalog.catalog_item n
                    WHERE\s"""
                            + where
                            + " ORDER BY n.depth ASC, n.display_order ASC, n.code_normalized ASC, n.id ASC";
            rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapTreeRow(rs), params.toArray());
        }

        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> itemIds = rows.stream().map(TreeRow::id).toList();
        Map<UUID, Long> childCounts = catalogSupportDao.countDirectChildren(itemIds);
        Set<UUID> inactiveEffective = catalogSupportDao.findInactiveEffectiveItemIds(groupId, itemIds);
        Map<UUID, List<CatalogItemRefResponse>> ownRefs = catalogSupportDao.loadOwnRefs(itemIds);
        Map<UUID, List<CatalogInheritedRefResponse>> inheritedRefs =
                catalogSupportDao.loadInheritedRefs(groupId, itemIds);

        return rows.stream()
                .map(r -> {
                    long children = childCounts.getOrDefault(r.id(), 0L);
                    CatalogStatus eff = inactiveEffective.contains(r.id())
                            ? CatalogStatus.INACTIVE
                            : CatalogStatus.ACTIVE;
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
                            eff,
                            r.parentId(),
                            r.depth(),
                            r.path(),
                            children,
                            children > 0,
                            matchedIds.contains(r.id()),
                            ownRefs.getOrDefault(r.id(), List.of()),
                            inheritedRefs.getOrDefault(r.id(), List.of()),
                            r.version());
                })
                .toList();
    }

    @Transactional
    public CatalogItemResponse moveItem(UUID itemId, MoveCatalogItemRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (group.getKind() != CatalogGroupKind.TREE) {
            throw new ConflictException(ERR_PARENT_INVALID);
        }
        if (request.version() != null && !Objects.equals(item.getVersion(), request.version())) {
            throw new VersionConflictException();
        }
        UUID newParentId = request.parentId();
        if (newParentId == null || item.getParentId() == null) {
            throw new ConflictException(ERR_PARENT_INVALID);
        }
        if (newParentId.equals(item.getParentId())) {
            return toItemResponses(group, List.of(item)).getFirst();
        }

        CatalogItem newParent = catalogItemRepository.findById(newParentId).orElse(null);
        if (newParent == null || !newParent.getGroupId().equals(group.getId())) {
            throw new ConflictException(ERR_PARENT_INVALID);
        }
        if (newParent.getId().equals(item.getId())
                || newParent.getPath().startsWith(item.getPath())) {
            throw new ConflictException(ERR_PARENT_INVALID);
        }
        if (newParent.getDepth() != item.getDepth() - 1) {
            throw new ConflictException(ERR_PARENT_INVALID);
        }
        Set<UUID> inactiveParent =
                catalogSupportDao.findInactiveEffectiveItemIds(group.getId(), List.of(newParentId));
        if (newParent.getStatus() != CatalogStatus.ACTIVE || inactiveParent.contains(newParentId)) {
            throw new ConflictException(ERR_PARENT_INACTIVE);
        }

        String oldPrefix = item.getPath();
        String newPrefix = newParent.getPath() + item.getId() + "/";
        UUID oldParentId = item.getParentId();
        Instant now = Instant.now(clock);

        int newOrder = catalogItemRepository.findMaxDisplayOrder(group.getId(), newParentId) + 1;
        jdbcTemplate.update(
                """
                UPDATE catalog.catalog_item
                SET parent_id = ?,
                    display_order = ?,
                    version = version + 1,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                """,
                newParentId,
                newOrder,
                java.sql.Timestamp.from(now),
                actor.id(),
                itemId);

        jdbcTemplate.update(
                """
                UPDATE catalog.catalog_item
                SET path = ? || substring(path FROM ?),
                    updated_at = ?,
                    updated_by = ?
                WHERE group_id = ?
                  AND path LIKE ? ESCAPE '\\'
                """,
                newPrefix,
                oldPrefix.length() + 1,
                java.sql.Timestamp.from(now),
                actor.id(),
                group.getId(),
                escapeLike(oldPrefix) + "%");

        entityManager.flush();
        entityManager.clear();

        CatalogItem reloaded = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        catalogCacheService.evictGroupAndDependents(group.getId(), group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_ITEM_MOVED",
                "CATALOG_ITEM",
                itemId,
                reloaded.getCode(),
                Map.of("parentId", oldParentId.toString(), "path", oldPrefix),
                Map.of("parentId", newParentId.toString(), "path", reloaded.getPath()),
                null);

        return toItemResponses(group, List.of(reloaded)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<CatalogGroupRefResponse> listGroupRefGroups(UUID groupId) {
        if (!catalogGroupRepository.existsById(groupId)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        return catalogSupportDao.loadGroupRefs(List.of(groupId)).getOrDefault(groupId, List.of());
    }

    @Transactional
    public List<CatalogGroupRefResponse> addGroupRefGroup(UUID groupId, UUID refGroupId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (group.getKind() != CatalogGroupKind.TREE) {
            throw new ValidationException(
                    ERR_REF_INVALID,
                    null,
                    List.of(new FieldError("groupId", ERR_REF_INVALID, "Chỉ nhóm TREE mới có danh mục liên kết")));
        }

        validateRefGroupsForAdd(groupId, List.of(refGroupId));

        List<CatalogGroupRef> existing =
                catalogGroupRefRepository.findByIdGroupIdOrderByPositionAsc(groupId);
        boolean alreadyExists = existing.stream()
                .anyMatch(r -> r.getRefGroupId().equals(refGroupId));
        if (!alreadyExists) {
            int nextPos = existing.stream().mapToInt(CatalogGroupRef::getPosition).max().orElse(-1) + 1;
            catalogGroupRefRepository.saveAndFlush(new CatalogGroupRef(groupId, refGroupId, nextPos, false));
            catalogCacheService.evictGroupAndDependents(groupId, group.getCodeNormalized());
            recordAudit(
                    actor,
                    "CATALOG_GROUP_UPDATED",
                    "CATALOG_GROUP",
                    groupId,
                    group.getCode(),
                    null,
                    Map.of("addedRefGroupId", refGroupId.toString()),
                    null);
        }
        return catalogSupportDao.loadGroupRefs(List.of(groupId)).getOrDefault(groupId, List.of());
    }

    @Transactional
    public void removeGroupRefGroup(UUID groupId, UUID refGroupId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogGroup group = catalogGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        List<CatalogGroupRef> existing =
                catalogGroupRefRepository.findByIdGroupIdOrderByPositionAsc(groupId);
        CatalogGroupRef target = existing.stream()
                .filter(r -> r.getRefGroupId().equals(refGroupId))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }
        if (target.isDefaultRef()) {
            throw new ConflictException(ERR_REF_GROUP_DEFAULT);
        }
        if (catalogItemRefRepository.countLinkedItems(groupId, refGroupId) > 0) {
            throw new ConflictException(ERR_REF_GROUP_IN_USE);
        }
        catalogGroupRefRepository.delete(target);
        catalogGroupRefRepository.flush();

        catalogCacheService.evictGroupAndDependents(groupId, group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_GROUP_UPDATED",
                "CATALOG_GROUP",
                groupId,
                group.getCode(),
                Map.of("removedRefGroupId", refGroupId.toString()),
                null,
                null);
    }

    @Transactional
    public CatalogItemResponse addItemRef(UUID itemId, UUID refItemId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (group.getKind() != CatalogGroupKind.TREE) {
            throw new ValidationException(
                    ERR_REF_INVALID,
                    null,
                    List.of(new FieldError("itemId", ERR_REF_INVALID, "Chỉ nút thuộc nhóm TREE mới gắn liên kết")));
        }

        validateItemRefsForAdd(group.getId(), List.of(refItemId), Set.of());
        CatalogItemRefId refId = new CatalogItemRefId(itemId, refItemId);
        if (!catalogItemRefRepository.existsById(refId)) {
            List<CatalogItemRefResponse> beforeRefs =
                    catalogSupportDao.loadOwnRefs(List.of(itemId)).getOrDefault(itemId, List.of());
            Instant now = Instant.now(clock);
            catalogItemRefRepository.saveAndFlush(new CatalogItemRef(itemId, refItemId, actor.id(), now));
            List<CatalogItemRefResponse> afterRefs =
                    catalogSupportDao.loadOwnRefs(List.of(itemId)).getOrDefault(itemId, List.of());
            catalogCacheService.evictGroupAndDependents(group.getId(), group.getCodeNormalized());
            recordAudit(
                    actor,
                    "CATALOG_ITEM_REFS_UPDATED",
                    "CATALOG_ITEM",
                    itemId,
                    item.getCode(),
                    Map.of("ownRefs", toRefIds(beforeRefs)),
                    Map.of("ownRefs", toRefIds(afterRefs)),
                    null);
        }
        return toItemResponses(group, List.of(item)).getFirst();
    }

    @Transactional
    public void removeItemRef(UUID itemId, UUID refItemId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        CatalogGroup group = catalogGroupRepository.findById(item.getGroupId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        CatalogItemRefId refId = new CatalogItemRefId(itemId, refItemId);
        if (!catalogItemRefRepository.existsById(refId)) {
            return;
        }
        List<CatalogItemRefResponse> beforeRefs =
                catalogSupportDao.loadOwnRefs(List.of(itemId)).getOrDefault(itemId, List.of());
        catalogItemRefRepository.deleteById(refId);
        catalogItemRefRepository.flush();
        List<CatalogItemRefResponse> afterRefs =
                catalogSupportDao.loadOwnRefs(List.of(itemId)).getOrDefault(itemId, List.of());

        catalogCacheService.evictGroupAndDependents(group.getId(), group.getCodeNormalized());
        recordAudit(
                actor,
                "CATALOG_ITEM_REFS_UPDATED",
                "CATALOG_ITEM",
                itemId,
                item.getCode(),
                Map.of("ownRefs", toRefIds(beforeRefs)),
                Map.of("ownRefs", toRefIds(afterRefs)),
                null);
    }

    private void replaceGroupRefs(UUID groupId, List<UUID> requestedRefIds) {
        List<CatalogGroupRef> existing =
                catalogGroupRefRepository.findByIdGroupIdOrderByPositionAsc(groupId);
        Map<UUID, CatalogGroupRef> existingMap = new LinkedHashMap<>();
        for (CatalogGroupRef ref : existing) {
            existingMap.put(ref.getRefGroupId(), ref);
        }
        Set<UUID> requestedSet = new LinkedHashSet<>(requestedRefIds);

        for (CatalogGroupRef ref : existing) {
            if (!requestedSet.contains(ref.getRefGroupId())) {
                if (ref.isDefaultRef()) {
                    throw new ConflictException(ERR_REF_GROUP_DEFAULT);
                }
                if (catalogItemRefRepository.countLinkedItems(groupId, ref.getRefGroupId()) > 0) {
                    throw new ConflictException(ERR_REF_GROUP_IN_USE);
                }
            }
        }

        List<UUID> newlyAdded = new ArrayList<>();
        for (UUID refId : requestedRefIds) {
            if (!existingMap.containsKey(refId)) {
                newlyAdded.add(refId);
            }
        }
        if (!newlyAdded.isEmpty()) {
            validateRefGroupsForAdd(groupId, newlyAdded);
        }

        for (CatalogGroupRef ref : existing) {
            if (!requestedSet.contains(ref.getRefGroupId())) {
                catalogGroupRefRepository.delete(ref);
            }
        }
        catalogGroupRefRepository.flush();

        for (int i = 0; i < requestedRefIds.size(); i++) {
            UUID refId = requestedRefIds.get(i);
            CatalogGroupRef existingRef = existingMap.get(refId);
            if (existingRef != null) {
                existingRef.setPosition(i);
                catalogGroupRefRepository.save(existingRef);
            } else {
                catalogGroupRefRepository.save(new CatalogGroupRef(groupId, refId, i, false));
            }
        }
        catalogGroupRefRepository.flush();
    }

    private void validateRefGroupsForAdd(UUID selfGroupId, List<UUID> refGroupIds) {
        Map<UUID, CatalogGroup> loaded = new HashMap<>();
        for (CatalogGroup g : catalogGroupRepository.findAllById(refGroupIds)) {
            loaded.put(g.getId(), g);
        }
        for (UUID refGroupId : refGroupIds) {
            if (selfGroupId != null && selfGroupId.equals(refGroupId)) {
                throw new ValidationException(
                        ERR_REF_INVALID,
                        null,
                        List.of(new FieldError("refGroupIds", ERR_REF_INVALID, "Không thể liên kết tới chính nhóm hiện tại")));
            }
            CatalogGroup target = loaded.get(refGroupId);
            if (target == null
                    || target.getKind() != CatalogGroupKind.SIMPLE
                    || target.getStatus() != CatalogStatus.ACTIVE) {
                throw new ValidationException(
                        ERR_REF_INVALID,
                        null,
                        List.of(new FieldError("refGroupIds", ERR_REF_INVALID, "Nhóm liên kết phải là nhóm SIMPLE đang hoạt động")));
            }
        }
    }

    private void validateItemRefsForAdd(
            UUID treeGroupId, List<UUID> refItemIds, Set<UUID> existingOwnRefIds) {
        if (refItemIds.isEmpty()) {
            return;
        }
        Set<UUID> allowedRefGroupIds = new HashSet<>();
        for (CatalogGroupRef ref : catalogGroupRefRepository.findByIdGroupIdOrderByPositionAsc(treeGroupId)) {
            allowedRefGroupIds.add(ref.getRefGroupId());
        }
        Map<UUID, CatalogItem> refItemsById = new HashMap<>();
        for (CatalogItem refItem : catalogItemRepository.findAllById(refItemIds)) {
            refItemsById.put(refItem.getId(), refItem);
        }
        for (UUID refItemId : refItemIds) {
            CatalogItem refItem = refItemsById.get(refItemId);
            if (refItem == null || !allowedRefGroupIds.contains(refItem.getGroupId())) {
                throw new ValidationException(
                        ERR_REF_INVALID,
                        null,
                        List.of(new FieldError("refItemIds", ERR_REF_INVALID, "Giá trị liên kết không thuộc nhóm đã khai báo")));
            }
            boolean alreadyLinked = existingOwnRefIds.contains(refItemId);
            if (!alreadyLinked && refItem.getStatus() != CatalogStatus.ACTIVE) {
                throw new ValidationException(
                        ERR_REF_INVALID,
                        null,
                        List.of(new FieldError("refItemIds", ERR_REF_INVALID, "Không thể gắn liên kết tới giá trị đã ngừng sử dụng")));
            }
        }
    }

    private List<CatalogItemResponse> toItemResponses(CatalogGroup group, List<CatalogItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<UUID> itemIds = items.stream().map(CatalogItem::getId).toList();
        Map<UUID, Long> childCounts = catalogSupportDao.countDirectChildren(itemIds);
        Set<UUID> inactiveEffective = catalogSupportDao.findInactiveEffectiveItemIds(group.getId(), itemIds);
        Map<UUID, List<CatalogItemRefResponse>> ownRefs = catalogSupportDao.loadOwnRefs(itemIds);
        Map<UUID, List<CatalogInheritedRefResponse>> inheritedRefs =
                catalogSupportDao.loadInheritedRefs(group.getId(), itemIds);

        return items.stream()
                .map(item -> {
                    CatalogStatus eff = inactiveEffective.contains(item.getId())
                            ? CatalogStatus.INACTIVE
                            : CatalogStatus.ACTIVE;
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
                            eff,
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
                })
                .toList();
    }

    private static CatalogGroupResponse toGroupResponse(
            CatalogGroup group, long itemCount, List<CatalogGroupRefResponse> refGroups) {
        return new CatalogGroupResponse(
                group.getId(),
                group.getCode(),
                group.getNameVi(),
                group.getNameEn(),
                group.getDescription(),
                group.getStatus(),
                group.getType(),
                group.getKind(),
                group.getLevels(),
                group.isSystem(),
                itemCount,
                refGroups,
                group.getVersion(),
                group.getCreatedAt(),
                group.getUpdatedAt());
    }

    private static String validateAndTrimCode(String rawCode) {
        if (rawCode == null) {
            throw new ValidationException(List.of(
                    new FieldError("code", "NOT_BLANK", "Mã không được để trống")));
        }
        String trimmed = rawCode.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("code", "NOT_BLANK", "Mã không được để trống")));
        }
        if (!CatalogNormalizer.CODE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(List.of(
                    new FieldError("code", "PATTERN", "Mã chỉ gồm chữ cái không dấu, số, _, ., - và tối đa 50 ký tự")));
        }
        return trimmed;
    }

    private static List<String> validateLevels(List<String> rawLevels) {
        if (rawLevels == null || rawLevels.size() < 2 || rawLevels.size() > 6) {
            throw new ValidationException(List.of(
                    new FieldError("levels", "INVALID_LEVELS", "Danh mục cây phải có từ 2 đến 6 cấp")));
        }
        List<String> cleaned = new ArrayList<>(rawLevels.size());
        Set<String> seen = new HashSet<>();
        for (String level : rawLevels) {
            if (level == null || level.isBlank()) {
                throw new ValidationException(List.of(
                        new FieldError("levels", "NOT_BLANK", "Tên cấp không được để trống")));
            }
            String trimmed = level.trim();
            String key = trimmed.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new ValidationException(List.of(
                        new FieldError("levels", "DUPLICATE", "Tên các cấp không được trùng nhau")));
            }
            cleaned.add(trimmed);
        }
        return cleaned;
    }

    private static List<UUID> deduplicateIds(List<UUID> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> seen = new LinkedHashSet<>();
        for (UUID id : rawIds) {
            if (id == null) {
                throw new ValidationException(List.of(
                        new FieldError("ids", "INVALID", "Danh sách ID chứa phần tử trống")));
            }
            seen.add(id);
        }
        return List.copyOf(seen);
    }

    private void recordAudit(
            CurrentUser actor,
            String action,
            String targetType,
            UUID targetId,
            String targetLabel,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        AuditActorType actorType = actor.realm() == Realm.MASTER ? AuditActorType.PLATFORM : AuditActorType.USER;
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .target(targetType, targetId.toString(), targetLabel)
                .before(before)
                .after(after)
                .reason(reason)
                .actor(actor.id(), actor.username(), null, actorType)
                .build());
    }

    private static Map<String, Object> groupAuditState(
            CatalogGroup group, List<CatalogGroupRefResponse> refs) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", group.getCode());
        state.put("nameVi", group.getNameVi());
        state.put("nameEn", group.getNameEn());
        state.put("description", group.getDescription());
        state.put("status", group.getStatus().name());
        state.put("type", group.getType().name());
        state.put("kind", group.getKind().name());
        state.put("levels", group.getLevels());
        state.put("system", group.isSystem());
        state.put("refGroupIds", refs.stream().map(r -> r.refGroupId().toString()).toList());
        return state;
    }

    private static Map<String, Object> itemAuditState(
            CatalogItem item, List<CatalogItemRefResponse> ownRefs) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("groupId", item.getGroupId().toString());
        state.put("code", item.getCode());
        state.put("value", item.getValue());
        state.put("nameVi", item.getNameVi());
        state.put("nameEn", item.getNameEn());
        state.put("description", item.getDescription());
        state.put("displayOrder", item.getDisplayOrder());
        state.put("status", item.getStatus().name());
        state.put("system", item.isSystem());
        state.put("parentId", item.getParentId() == null ? null : item.getParentId().toString());
        state.put("depth", item.getDepth());
        state.put("path", item.getPath());
        state.put("ownRefItemIds", toRefIds(ownRefs));
        return state;
    }

    private static List<String> toRefIds(List<CatalogItemRefResponse> refs) {
        return refs.stream().map(r -> r.refItemId().toString()).toList();
    }

    private static String buildGroupOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY g.is_system DESC, g.created_at ASC, g.id ASC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String col = switch (order.getProperty()) {
                case "code" -> "g.code";
                case "nameVi" -> "g.name_vi";
                case "nameEn" -> "g.name_en";
                case "status" -> "g.status";
                case "type" -> "g.type";
                case "kind" -> "g.kind";
                case "createdAt" -> "g.created_at";
                case "updatedAt" -> "g.updated_at";
                default -> null;
            };
            if (col != null) {
                clauses.add(col + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        if (clauses.isEmpty()) {
            return "ORDER BY g.is_system DESC, g.created_at ASC, g.id ASC";
        }
        clauses.add("g.id ASC");
        return "ORDER BY " + String.join(", ", clauses);
    }

    private static String buildItemOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY i.display_order ASC, i.code_normalized ASC, i.id ASC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String col = switch (order.getProperty()) {
                case "displayOrder" -> "i.display_order";
                case "code" -> "i.code_normalized";
                case "value" -> "i.value";
                case "nameVi" -> "i.name_vi";
                case "nameEn" -> "i.name_en";
                case "status" -> "i.status";
                case "createdAt" -> "i.created_at";
                case "updatedAt" -> "i.updated_at";
                default -> null;
            };
            if (col != null) {
                clauses.add(col + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        if (clauses.isEmpty()) {
            return "ORDER BY i.display_order ASC, i.code_normalized ASC, i.id ASC";
        }
        clauses.add("i.id ASC");
        return "ORDER BY " + String.join(", ", clauses);
    }

    private static TreeRow mapTreeRow(ResultSet rs) throws SQLException {
        return new TreeRow(
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
                rs.getLong("version"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
