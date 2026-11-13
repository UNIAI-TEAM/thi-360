package vn.unicom.thi360.classroom.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.classroom.api.AddClassMembersRequest;
import vn.unicom.thi360.classroom.api.AddClassMembersResponse;
import vn.unicom.thi360.classroom.api.ClassDetailResponse;
import vn.unicom.thi360.classroom.api.ClassMemberResponse;
import vn.unicom.thi360.classroom.api.ClassSummaryResponse;
import vn.unicom.thi360.classroom.api.CreateClassRequest;
import vn.unicom.thi360.classroom.api.TransferClassMemberRequest;
import vn.unicom.thi360.classroom.api.TransferClassMemberResponse;
import vn.unicom.thi360.classroom.api.UpdateClassRequest;
import vn.unicom.thi360.classroom.api.UpdateClassStatusRequest;
import vn.unicom.thi360.classroom.api.UserClassHistoryResponse;
import vn.unicom.thi360.classroom.api.UserClassesResponse;
import vn.unicom.thi360.classroom.api.UserCurrentClassResponse;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;
import vn.unicom.thi360.classroom.domain.ClassMemberHistoryAction;
import vn.unicom.thi360.classroom.domain.ClassNormalizer;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberHistoryRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
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

@Service
public class ClassService {

    public static final Set<String> ALLOWED_CLASS_SORT_FIELDS =
            Set.of("code", "name", "status", "createdAt", "updatedAt");
    public static final Set<String> ALLOWED_MEMBER_SORT_FIELDS =
            Set.of("joinedAt", "username", "fullName");

    private static final String MODULE = "CLASSROOM";
    private static final String TARGET_TYPE_CLASS = "CLASS";
    private static final String ERROR_CODE_TAKEN = "CLASS_CODE_TAKEN";
    private static final String ERROR_CODE_INVALID = "CLASS_CODE_INVALID";
    private static final String ERROR_CLASS_INACTIVE = "CLASS_INACTIVE";
    private static final String ERROR_ALREADY_MEMBER = "CLASS_ALREADY_MEMBER";
    private static final String ERROR_TRANSFER_SAME_CLASS = "CLASS_TRANSFER_SAME_CLASS";

    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassMemberHistoryRepository classMemberHistoryRepository;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ClassService(
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.classRoomRepository = classRoomRepository;
        this.classMemberRepository = classMemberRepository;
        this.classMemberHistoryRepository = classMemberHistoryRepository;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassSummaryResponse> listClasses(
            String q, ClassStatus status, PageQuery pageQuery) {
        StringBuilder where = new StringBuilder("c.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();

        if (status != null) {
            where.append(" AND c.status = ?");
            params.add(status.code());
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            where.append(
                    " AND (LOWER(c.code) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(c.name) LIKE ? ESCAPE '\\'"
                            + " OR LOWER(COALESCE(c.description, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }

        String countSql = "SELECT COUNT(*) FROM classroom.class_room c WHERE " + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;
        if (totalCount == 0L) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        String orderBy = buildClassOrderBy(pageQuery.sort());
        String dataSql =
                """
                SELECT c.id,
                       c.code,
                       c.name,
                       c.description,
                       c.owner_user_id,
                       c.status,
                       c.version,
                       c.created_at,
                       c.updated_at,
                       (SELECT COUNT(*) FROM classroom.class_member cm WHERE cm.class_id = c.id) AS member_count
                FROM classroom.class_room c
                WHERE\s"""
                        + where
                        + " "
                        + orderBy
                        + " LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageQuery.size());
        dataParams.add((long) pageQuery.page() * pageQuery.size());

        List<ClassRow> rows = jdbcTemplate.query(dataSql, (rs, rowNum) -> mapClassRow(rs), dataParams.toArray());
        Set<UUID> ownerIds = new LinkedHashSet<>();
        for (ClassRow row : rows) {
            if (row.ownerUserId() != null) {
                ownerIds.add(row.ownerUserId());
            }
        }
        Map<UUID, UserDirectory.UserSummary> owners = userDirectory.findByIds(ownerIds);

        List<ClassSummaryResponse> items = rows.stream()
                .map(row -> {
                    UserDirectory.UserSummary owner = row.ownerUserId() == null
                            ? null
                            : owners.get(row.ownerUserId());
                    return new ClassSummaryResponse(
                            row.id(),
                            row.code(),
                            row.name(),
                            row.description(),
                            row.ownerUserId(),
                            owner == null ? null : owner.username(),
                            owner == null ? null : owner.fullName(),
                            row.status(),
                            row.memberCount(),
                            row.version(),
                            row.createdAt(),
                            row.updatedAt());
                })
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageQuery.toPageable(), totalCount));
    }

    @Transactional(readOnly = true)
    public ClassDetailResponse getClass(UUID classId) {
        ClassRoom classRoom = classRoomRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        long memberCount = countMembers(classId);
        return toDetailResponse(classRoom, memberCount);
    }

    @Transactional
    public ClassDetailResponse createClass(CreateClassRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String trimmedCode = validateAndTrimCode(request.code());
        String codeNormalized = ClassNormalizer.normalizeCode(trimmedCode);
        String trimmedName = validateRequiredName(request.name());
        String trimmedDescription = validateOptionalDescription(request.description());

        if (classRoomRepository.existsByCodeNormalizedAndDeletedAtIsNull(codeNormalized)) {
            throw new ConflictException(ERROR_CODE_TAKEN);
        }

        if (request.ownerUserId() != null) {
            validateOwnerUser(request.ownerUserId());
        }

        ClassStatus initialStatus = request.status() == null ? ClassStatus.ACTIVE : request.status();
        Instant now = Instant.now(clock);
        ClassRoom classRoom = new ClassRoom(
                Ids.newId(),
                trimmedCode,
                trimmedName,
                trimmedDescription,
                request.ownerUserId(),
                initialStatus,
                now,
                actor.id());
        classRoom = classRoomRepository.saveAndFlush(classRoom);

        Map<String, Object> after = classAuditState(classRoom);
        recordAudit("CLASS_CREATED", classRoom.getId(), classRoom.getCode(), null, after, null);

        return toDetailResponse(classRoom, 0L);
    }

    @Transactional
    public ClassDetailResponse updateClass(UUID classId, UpdateClassRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ClassRoom classRoom = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (!Objects.equals(classRoom.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        Map<String, Object> before = classAuditState(classRoom);

        String newCode = null;
        if (request.code() != null) {
            String trimmedCode = validateAndTrimCode(request.code());
            String codeNormalized = ClassNormalizer.normalizeCode(trimmedCode);
            if (!codeNormalized.equals(classRoom.getCodeNormalized())
                    && classRoomRepository.existsByCodeNormalizedAndDeletedAtIsNullAndIdNot(codeNormalized, classId)) {
                throw new ConflictException(ERROR_CODE_TAKEN);
            }
            newCode = trimmedCode;
        }

        String newName = null;
        if (request.name() != null) {
            newName = validateRequiredName(request.name());
        }

        boolean updateDescription = request.description() != null;
        String newDescription = updateDescription ? validateOptionalDescription(request.description()) : null;

        boolean updateOwner = false;
        UUID newOwnerUserId = null;
        if (request.ownerUserId() != null) {
            validateOwnerUser(request.ownerUserId());
            updateOwner = true;
            newOwnerUserId = request.ownerUserId();
        } else if (Boolean.TRUE.equals(request.clearOwner())) {
            updateOwner = true;
            newOwnerUserId = null;
        }

        ClassStatus previousStatus = classRoom.getStatus();
        boolean deactivating = previousStatus == ClassStatus.ACTIVE && request.status() == ClassStatus.INACTIVE;

        Instant now = Instant.now(clock);
        classRoom.updateDetails(
                newCode,
                newName,
                newDescription,
                updateDescription,
                newOwnerUserId,
                updateOwner,
                request.status(),
                now,
                actor.id());
        classRoom = classRoomRepository.saveAndFlush(classRoom);

        Map<String, Object> after = classAuditState(classRoom);
        String action = deactivating ? "CLASS_DEACTIVATED" : "CLASS_UPDATED";
        recordAudit(action, classRoom.getId(), classRoom.getCode(), before, after, null);

        return toDetailResponse(classRoom, countMembers(classId));
    }

    @Transactional
    public ClassDetailResponse updateStatus(UUID classId, UpdateClassStatusRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ClassRoom classRoom = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (!Objects.equals(classRoom.getVersion(), request.version())) {
            throw new VersionConflictException();
        }

        String trimmedReason = validateOptionalReason(request.reason());
        ClassStatus previousStatus = classRoom.getStatus();
        Map<String, Object> before = Map.of("status", previousStatus.name());

        Instant now = Instant.now(clock);
        classRoom.updateStatus(request.status(), now, actor.id());
        classRoom = classRoomRepository.saveAndFlush(classRoom);

        Map<String, Object> after = Map.of("status", classRoom.getStatus().name());
        String action = (previousStatus == ClassStatus.ACTIVE && request.status() == ClassStatus.INACTIVE)
                ? "CLASS_DEACTIVATED"
                : "CLASS_UPDATED";
        recordAudit(action, classRoom.getId(), classRoom.getCode(), before, after, trimmedReason);

        return toDetailResponse(classRoom, countMembers(classId));
    }

    @Transactional
    public void deleteClass(UUID classId, String reason) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        ClassRoom classRoom = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        String trimmedReason = validateOptionalReason(reason);
        Map<String, Object> before = classAuditState(classRoom);

        Instant now = Instant.now(clock);
        classRoom.softDelete(trimmedReason, now, actor.id());
        classRoom = classRoomRepository.saveAndFlush(classRoom);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("deletedAt", classRoom.getDeletedAt().toString());
        if (trimmedReason != null) {
            after.put("deleteReason", trimmedReason);
        }
        recordAudit("CLASS_DELETED", classRoom.getId(), classRoom.getCode(), before, after, trimmedReason);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassMemberResponse> listMembers(
            UUID classId, String q, PageQuery pageQuery) {
        classRoomRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        List<ClassMember> members = classMemberRepository.findByIdClassIdOrderByJoinedAtDescIdUserIdAsc(classId);
        if (members.isEmpty()) {
            return PageResponse.from(new PageImpl<>(List.of(), pageQuery.toPageable(), 0L));
        }

        Set<UUID> userIds = new LinkedHashSet<>();
        Set<UUID> addedByIds = new LinkedHashSet<>();
        for (ClassMember member : members) {
            userIds.add(member.getUserId());
            if (member.getAddedBy() != null) {
                addedByIds.add(member.getAddedBy());
            }
        }

        Map<UUID, UserDirectory.UserDetailSummary> userDetails = userDirectory.findDetailsByIds(userIds);
        Map<UUID, UserDirectory.UserSummary> addedByUsers = userDirectory.findByIds(addedByIds);

        String needle = (q == null || q.isBlank()) ? null : q.trim().toLowerCase(Locale.ROOT);
        List<ClassMemberResponse> matched = new ArrayList<>();
        for (ClassMember member : members) {
            UserDirectory.UserDetailSummary user = userDetails.get(member.getUserId());
            if (user == null || user.deleted()) {
                continue;
            }
            if (needle != null && !matchesMemberSearch(user, needle)) {
                continue;
            }
            UserDirectory.UserSummary addedBySummary = member.getAddedBy() == null
                    ? null
                    : addedByUsers.get(member.getAddedBy());
            matched.add(new ClassMemberResponse(
                    user.id(),
                    user.code(),
                    user.username(),
                    user.fullName(),
                    user.email(),
                    user.phone(),
                    user.status(),
                    member.getJoinedAt(),
                    member.getAddedBy(),
                    addedBySummary == null ? null : addedBySummary.username()));
        }

        matched.sort(buildMemberComparator(pageQuery.sort()));

        int total = matched.size();
        int fromIndex = Math.min(pageQuery.page() * pageQuery.size(), total);
        int toIndex = Math.min(fromIndex + pageQuery.size(), total);
        List<ClassMemberResponse> pageContent = matched.subList(fromIndex, toIndex);
        return PageResponse.from(new PageImpl<>(pageContent, pageQuery.toPageable(), total));
    }

    @Transactional
    public AddClassMembersResponse addMembers(UUID classId, AddClassMembersRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (request.userIds() == null || request.userIds().isEmpty()) {
            throw new ValidationException(List.of(
                    new FieldError("userIds", "NOTEMPTY", "Danh sách học viên không được để trống")));
        }

        Set<UUID> uniqueRequested = new LinkedHashSet<>();
        for (UUID uid : request.userIds()) {
            if (uid == null) {
                throw new ValidationException(List.of(
                        new FieldError("userIds", "INVALID", "ID học viên không được để trống")));
            }
            uniqueRequested.add(uid);
        }

        ClassRoom classRoom = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (classRoom.getStatus() == ClassStatus.INACTIVE) {
            throw new ConflictException(ERROR_CLASS_INACTIVE);
        }

        Map<UUID, UserDirectory.UserDetailSummary> userDetails = userDirectory.findDetailsByIds(uniqueRequested);
        for (UUID uid : uniqueRequested) {
            UserDirectory.UserDetailSummary summary = userDetails.get(uid);
            if (summary == null || !summary.isAvailableForMembership()) {
                throw new NotFoundException("NOT_FOUND", null);
            }
        }

        Set<UUID> existingMembers = new HashSet<>();
        for (ClassMember member : classMemberRepository.findByIdClassIdOrderByJoinedAtDescIdUserIdAsc(classId)) {
            existingMembers.add(member.getUserId());
        }

        Instant now = Instant.now(clock);
        List<UUID> addedUserIds = new ArrayList<>();
        List<UUID> skippedUserIds = new ArrayList<>();
        Set<UUID> seenInRequest = new HashSet<>();
        List<ClassMember> newMembers = new ArrayList<>();
        List<ClassMemberHistory> historyRows = new ArrayList<>();

        for (UUID uid : request.userIds()) {
            if (!seenInRequest.add(uid) || existingMembers.contains(uid)) {
                skippedUserIds.add(uid);
                continue;
            }
            newMembers.add(new ClassMember(classId, uid, now, actor.id()));
            historyRows.add(new ClassMemberHistory(
                    Ids.newId(),
                    classId,
                    uid,
                    ClassMemberHistoryAction.JOIN,
                    null,
                    actor.id(),
                    null,
                    now));
            addedUserIds.add(uid);
        }

        if (!newMembers.isEmpty()) {
            classMemberRepository.saveAll(newMembers);
            classMemberRepository.flush();
            classMemberHistoryRepository.saveAll(historyRows);
            classMemberHistoryRepository.flush();

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("addedUserIds", addedUserIds.stream().map(UUID::toString).toList());
            after.put("addedCount", addedUserIds.size());
            if (!skippedUserIds.isEmpty()) {
                after.put("skippedUserIds", skippedUserIds.stream().map(UUID::toString).toList());
            }
            recordAudit("CLASS_MEMBER_ADDED", classRoom.getId(), classRoom.getCode(), null, after, null);
        }

        return new AddClassMembersResponse(
                classId,
                List.copyOf(addedUserIds),
                List.copyOf(skippedUserIds),
                addedUserIds.size(),
                skippedUserIds.size());
    }

    @Transactional
    public void removeMember(UUID classId, UUID userId, String reason) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        String trimmedReason = validateOptionalReason(reason);

        ClassRoom classRoom = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        ClassMember member = classMemberRepository.findByIdClassIdAndIdUserId(classId, userId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        classMemberRepository.delete(member);
        classMemberRepository.flush();

        Instant now = Instant.now(clock);
        classMemberHistoryRepository.saveAndFlush(new ClassMemberHistory(
                Ids.newId(),
                classId,
                userId,
                ClassMemberHistoryAction.LEAVE,
                null,
                actor.id(),
                trimmedReason,
                now));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("classId", classId.toString());
        before.put("userId", userId.toString());
        recordAudit("CLASS_MEMBER_REMOVED", classRoom.getId(), classRoom.getCode(), before, null, trimmedReason);
    }

    @Transactional
    public TransferClassMemberResponse transferMember(
            UUID sourceClassId, UUID userId, TransferClassMemberRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        UUID targetClassId = request.targetClassId();
        if (sourceClassId.equals(targetClassId)) {
            throw new ConflictException(ERROR_TRANSFER_SAME_CLASS);
        }
        String trimmedReason = validateOptionalReason(request.reason());

        // Lock both classes in deterministic UUID order to avoid deadlocks.
        UUID firstLockId = sourceClassId.compareTo(targetClassId) <= 0 ? sourceClassId : targetClassId;
        UUID secondLockId = firstLockId.equals(sourceClassId) ? targetClassId : sourceClassId;

        ClassRoom firstLocked = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(firstLockId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        ClassRoom secondLocked = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(secondLockId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        ClassRoom sourceClass = firstLocked.getId().equals(sourceClassId) ? firstLocked : secondLocked;
        ClassRoom targetClass = firstLocked.getId().equals(targetClassId) ? firstLocked : secondLocked;

        ClassMember sourceMember = classMemberRepository.findByIdClassIdAndIdUserId(sourceClassId, userId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        if (targetClass.getStatus() == ClassStatus.INACTIVE) {
            throw new ConflictException(ERROR_CLASS_INACTIVE);
        }
        if (classMemberRepository.existsByIdClassIdAndIdUserId(targetClassId, userId)) {
            throw new ConflictException(ERROR_ALREADY_MEMBER);
        }

        classMemberRepository.delete(sourceMember);
        classMemberRepository.flush();

        Instant now = Instant.now(clock);
        classMemberRepository.saveAndFlush(new ClassMember(targetClassId, userId, now, actor.id()));

        // Insert TRANSFER_OUT then TRANSFER_IN sequentially so seq preserves causal order even at identical Instant.
        classMemberHistoryRepository.saveAndFlush(new ClassMemberHistory(
                Ids.newId(),
                sourceClassId,
                userId,
                ClassMemberHistoryAction.TRANSFER_OUT,
                targetClassId,
                actor.id(),
                trimmedReason,
                now));
        classMemberHistoryRepository.saveAndFlush(new ClassMemberHistory(
                Ids.newId(),
                targetClassId,
                userId,
                ClassMemberHistoryAction.TRANSFER_IN,
                sourceClassId,
                actor.id(),
                trimmedReason,
                now));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("classId", sourceClassId.toString());
        before.put("classCode", sourceClass.getCode());
        before.put("userId", userId.toString());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("classId", targetClassId.toString());
        after.put("classCode", targetClass.getCode());
        after.put("userId", userId.toString());

        recordAudit("CLASS_MEMBER_TRANSFERRED", sourceClass.getId(), sourceClass.getCode(), before, after, trimmedReason);

        return new TransferClassMemberResponse(sourceClassId, targetClassId, userId, now);
    }

    @Transactional(readOnly = true)
    public UserClassesResponse getUserClasses(UUID userId) {
        UserDirectory.UserDetailSummary user = userDirectory.findDetailById(userId)
                .filter(u -> !u.platformAdmin() && !"PROVISIONING".equals(u.status()))
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));

        List<CurrentClassRow> currentRows = jdbcTemplate.query(
                """
                SELECT cm.class_id,
                       c.code AS class_code,
                       c.name AS class_name,
                       c.status AS class_status,
                       cm.joined_at,
                       cm.added_by
                FROM classroom.class_member cm
                JOIN classroom.class_room c ON c.id = cm.class_id
                WHERE cm.user_id = ?
                  AND c.deleted_at IS NULL
                ORDER BY cm.joined_at DESC, c.code_normalized ASC
                """,
                (rs, rowNum) -> new CurrentClassRow(
                        rs.getObject("class_id", UUID.class),
                        rs.getString("class_code"),
                        rs.getString("class_name"),
                        ClassStatus.fromCode(rs.getShort("class_status")),
                        toInstant(rs.getTimestamp("joined_at")),
                        rs.getObject("added_by", UUID.class)),
                user.id());

        List<HistoryRow> historyRows = jdbcTemplate.query(
                """
                SELECT h.id,
                       h.class_id,
                       c.code AS class_code,
                       c.name AS class_name,
                       h.action,
                       h.related_class_id,
                       rc.code AS related_class_code,
                       rc.name AS related_class_name,
                       h.actor_id,
                       h.reason,
                       h.occurred_at
                FROM classroom.class_member_history h
                JOIN classroom.class_room c ON c.id = h.class_id
                LEFT JOIN classroom.class_room rc ON rc.id = h.related_class_id
                WHERE h.user_id = ?
                ORDER BY h.occurred_at DESC, h.seq DESC
                """,
                (rs, rowNum) -> new HistoryRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("class_id", UUID.class),
                        rs.getString("class_code"),
                        rs.getString("class_name"),
                        ClassMemberHistoryAction.fromCode(rs.getShort("action")),
                        rs.getObject("related_class_id", UUID.class),
                        rs.getString("related_class_code"),
                        rs.getString("related_class_name"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("reason"),
                        toInstant(rs.getTimestamp("occurred_at"))),
                user.id());

        Set<UUID> userLookupIds = new LinkedHashSet<>();
        for (CurrentClassRow row : currentRows) {
            if (row.addedBy() != null) {
                userLookupIds.add(row.addedBy());
            }
        }
        for (HistoryRow row : historyRows) {
            if (row.actorId() != null) {
                userLookupIds.add(row.actorId());
            }
        }
        Map<UUID, UserDirectory.UserSummary> actorSummaries = userDirectory.findByIds(userLookupIds);

        List<UserCurrentClassResponse> currentClasses = currentRows.stream()
                .map(row -> {
                    UserDirectory.UserSummary addedByUser = row.addedBy() == null
                            ? null
                            : actorSummaries.get(row.addedBy());
                    return new UserCurrentClassResponse(
                            row.classId(),
                            row.classCode(),
                            row.className(),
                            row.classStatus(),
                            row.joinedAt(),
                            row.addedBy(),
                            addedByUser == null ? null : addedByUser.username());
                })
                .toList();

        List<UserClassHistoryResponse> history = historyRows.stream()
                .map(row -> {
                    UserDirectory.UserSummary actorUser = row.actorId() == null
                            ? null
                            : actorSummaries.get(row.actorId());
                    return new UserClassHistoryResponse(
                            row.id(),
                            row.classId(),
                            row.classCode(),
                            row.className(),
                            row.action(),
                            row.relatedClassId(),
                            row.relatedClassCode(),
                            row.relatedClassName(),
                            row.actorId(),
                            actorUser == null ? null : actorUser.username(),
                            row.reason(),
                            row.occurredAt());
                })
                .toList();

        return new UserClassesResponse(user.id(), currentClasses, history);
    }

    private void validateOwnerUser(UUID ownerUserId) {
        UserDirectory.UserDetailSummary owner = userDirectory.findDetailById(ownerUserId)
                .filter(UserDirectory.UserDetailSummary::isAvailableForMembership)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        Objects.requireNonNull(owner);
    }

    private long countMembers(UUID classId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classroom.class_member WHERE class_id = ?",
                Long.class,
                classId);
        return count == null ? 0L : count;
    }

    private ClassDetailResponse toDetailResponse(ClassRoom classRoom, long memberCount) {
        UserDirectory.UserSummary owner = classRoom.getOwnerUserId() == null
                ? null
                : userDirectory.findById(classRoom.getOwnerUserId()).orElse(null);
        return new ClassDetailResponse(
                classRoom.getId(),
                classRoom.getCode(),
                classRoom.getName(),
                classRoom.getDescription(),
                classRoom.getOwnerUserId(),
                owner == null ? null : owner.username(),
                owner == null ? null : owner.fullName(),
                classRoom.getStatus(),
                memberCount,
                classRoom.getVersion(),
                classRoom.getCreatedAt(),
                classRoom.getCreatedBy(),
                classRoom.getUpdatedAt(),
                classRoom.getUpdatedBy());
    }

    private void recordAudit(
            String action,
            UUID targetId,
            String targetLabel,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason) {
        auditRecorder.record(AuditEvent.builder(MODULE, action)
                .target(TARGET_TYPE_CLASS, targetId.toString(), targetLabel)
                .before(before)
                .after(after)
                .reason(reason)
                .build());
    }

    private static Map<String, Object> classAuditState(ClassRoom classRoom) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", classRoom.getCode());
        map.put("name", classRoom.getName());
        map.put("description", classRoom.getDescription());
        map.put("ownerUserId", classRoom.getOwnerUserId() == null ? null : classRoom.getOwnerUserId().toString());
        map.put("status", classRoom.getStatus().name());
        return map;
    }

    private static String validateAndTrimCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError("code", "NOT_BLANK", "Mã lớp không được để trống")));
        }
        String trimmed = rawCode.trim();
        if (!ClassNormalizer.CODE_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(List.of(
                    new FieldError(
                            "code",
                            ERROR_CODE_INVALID,
                            "Mã lớp học chỉ gồm 2-30 ký tự chữ không dấu, số, '.', '_', '-', bắt đầu bằng chữ hoặc số")));
        }
        return trimmed;
    }

    private static String validateRequiredName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new ValidationException(List.of(
                    new FieldError("name", "NOT_BLANK", "Tên lớp không được để trống")));
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > 200) {
            throw new ValidationException(List.of(
                    new FieldError("name", "SIZE", "Tên lớp tối đa 200 ký tự")));
        }
        return trimmed;
    }

    private static String validateOptionalDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }
        String trimmed = ClassNormalizer.trimToNull(rawDescription);
        if (trimmed != null && trimmed.length() > 1000) {
            throw new ValidationException(List.of(
                    new FieldError("description", "SIZE", "Mô tả tối đa 1000 ký tự")));
        }
        return trimmed;
    }

    private static String validateOptionalReason(String rawReason) {
        if (rawReason == null) {
            return null;
        }
        String trimmed = ClassNormalizer.trimToNull(rawReason);
        if (trimmed != null && trimmed.length() > 500) {
            throw new ValidationException(List.of(
                    new FieldError("reason", "SIZE", "Lý do tối đa 500 ký tự")));
        }
        return trimmed;
    }

    private static boolean matchesMemberSearch(UserDirectory.UserDetailSummary user, String needle) {
        return containsIgnoreCase(user.code(), needle)
                || containsIgnoreCase(user.username(), needle)
                || containsIgnoreCase(user.fullName(), needle)
                || containsIgnoreCase(user.email(), needle)
                || containsIgnoreCase(user.phone(), needle);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static Comparator<ClassMemberResponse> buildMemberComparator(Sort sort) {
        Comparator<ClassMemberResponse> comparator = null;
        if (sort != null && !sort.isUnsorted()) {
            for (Sort.Order order : sort) {
                Comparator<ClassMemberResponse> next = switch (order.getProperty()) {
                    case "joinedAt" -> Comparator.comparing(ClassMemberResponse::joinedAt);
                    case "username" -> Comparator.comparing(
                            ClassMemberResponse::username,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    case "fullName" -> Comparator.comparing(
                            ClassMemberResponse::fullName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                    default -> null;
                };
                if (next != null) {
                    if (order.isDescending()) {
                        next = next.reversed();
                    }
                    comparator = comparator == null ? next : comparator.thenComparing(next);
                }
            }
        }
        if (comparator == null) {
            comparator = Comparator.comparing(ClassMemberResponse::joinedAt).reversed();
        }
        return comparator.thenComparing(ClassMemberResponse::userId);
    }

    private static String buildClassOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "ORDER BY c.created_at DESC, c.id DESC";
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String dir = order.isAscending() ? " ASC" : " DESC";
            String clause = switch (order.getProperty()) {
                case "code" -> "c.code_normalized" + dir;
                case "name" -> "LOWER(c.name)" + dir;
                case "status" -> "c.status" + dir;
                case "createdAt" -> "c.created_at" + dir;
                case "updatedAt" -> "c.updated_at" + dir;
                default -> null;
            };
            if (clause != null) {
                clauses.add(clause);
            }
        }
        if (clauses.isEmpty()) {
            return "ORDER BY c.created_at DESC, c.id DESC";
        }
        clauses.add("c.id DESC");
        return "ORDER BY " + String.join(", ", clauses);
    }

    private static ClassRow mapClassRow(ResultSet rs) throws SQLException {
        return new ClassRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("owner_user_id", UUID.class),
                ClassStatus.fromCode(rs.getShort("status")),
                rs.getLong("member_count"),
                rs.getLong("version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record ClassRow(
            UUID id,
            String code,
            String name,
            String description,
            UUID ownerUserId,
            ClassStatus status,
            long memberCount,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private record CurrentClassRow(
            UUID classId,
            String classCode,
            String className,
            ClassStatus classStatus,
            Instant joinedAt,
            UUID addedBy
    ) {
    }

    private record HistoryRow(
            UUID id,
            UUID classId,
            String classCode,
            String className,
            ClassMemberHistoryAction action,
            UUID relatedClassId,
            String relatedClassCode,
            String relatedClassName,
            UUID actorId,
            String reason,
            Instant occurredAt
    ) {
    }
}
