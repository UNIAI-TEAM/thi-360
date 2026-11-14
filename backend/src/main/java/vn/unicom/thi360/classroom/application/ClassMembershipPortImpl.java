package vn.unicom.thi360.classroom.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.classroom.api.UserClassesResponse;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;
import vn.unicom.thi360.classroom.domain.ClassMemberHistoryAction;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberHistoryRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.AppMessages;
@Service
public class ClassMembershipPortImpl implements ClassMembershipPort {

    private static final String MODULE = "CLASSROOM";
    private static final String TARGET_TYPE_CLASS = "CLASS";
    private static final String ERROR_CLASS_INACTIVE = "CLASS_INACTIVE";

    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassMemberHistoryRepository classMemberHistoryRepository;
    private final UserDirectory userDirectory;
    private final ClassMemberLock classMemberLock;
    private final AuditRecorder auditRecorder;
    private final AppMessages messages;
    private final ClassService classService;

    public ClassMembershipPortImpl(
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository,
            UserDirectory userDirectory,
            ClassMemberLock classMemberLock,
            ClassService classService,
            AuditRecorder auditRecorder) {
        this(
                classRoomRepository,
                classMemberRepository,
                classMemberHistoryRepository,
                userDirectory,
                classMemberLock,
                classService,
                auditRecorder,
                AppMessages.defaultInstance());
    }

    @Autowired
    public ClassMembershipPortImpl(
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository,
            UserDirectory userDirectory,
            ClassMemberLock classMemberLock,
            ClassService classService,
            AuditRecorder auditRecorder,
            AppMessages messages) {
        this.classRoomRepository = classRoomRepository;
        this.classMemberRepository = classMemberRepository;
        this.classMemberHistoryRepository = classMemberHistoryRepository;
        this.userDirectory = userDirectory;
        this.classMemberLock = classMemberLock;
        this.auditRecorder = auditRecorder;
        this.classService = classService;
        this.messages = messages;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> validateAssignableClasses(Collection<UUID> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID classId : classIds) {
            if (classId == null) {
                throw new ValidationException(List.of(
                        new FieldError("classIds", "INVALID", messages.get("validation.classMember.classIds.invalidItem"))));
            }
            uniqueIds.add(classId);
        }

        Map<UUID, ClassRoom> foundById = new HashMap<>();
        for (ClassRoom classRoom : classRoomRepository.findAllByIdInAndDeletedAtIsNull(uniqueIds)) {
            foundById.put(classRoom.getId(), classRoom);
        }
        if (foundById.size() != uniqueIds.size()) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        for (UUID classId : uniqueIds) {
            ClassRoom classRoom = foundById.get(classId);
            if (classRoom.getStatus() == ClassStatus.INACTIVE) {
                throw new ConflictException(ERROR_CLASS_INACTIVE);
            }
        }
        return List.copyOf(uniqueIds);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignUserToClasses(UUID userId, Collection<UUID> classIds, UUID actorId, Instant occurredAt) {
        if (classIds == null || classIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>();
        for (UUID classId : classIds) {
            if (classId == null) {
                throw new ValidationException(List.of(
                        new FieldError("classIds", "INVALID", messages.get("validation.classMember.classIds.invalidItem"))));
            }
            uniqueIds.add(classId);
        }

        List<UUID> sortedIds = uniqueIds.stream().sorted().toList();
        Map<UUID, ClassRoom> lockedById = new HashMap<>();
        for (UUID classId : sortedIds) {
            ClassRoom locked = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            if (locked.getStatus() == ClassStatus.INACTIVE) {
                throw new ConflictException(ERROR_CLASS_INACTIVE);
            }
            lockedById.put(classId, locked);
        }

        classMemberLock.lockUser(userId);
        UserDirectory.UserDetailSummary userSummary = userDirectory.findDetailById(userId)
                .filter(UserDirectory.UserDetailSummary::isAvailableForMembership)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
        if (userSummary.deleted()) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        List<ClassMember> newMembers = new ArrayList<>();
        List<ClassMemberHistory> historyRows = new ArrayList<>();
        List<ClassRoom> assignedClasses = new ArrayList<>();
        for (UUID classId : uniqueIds) {
            if (classMemberRepository.existsByIdClassIdAndIdUserId(classId, userId)) {
                continue;
            }
            newMembers.add(new ClassMember(classId, userId, occurredAt, actorId));
            historyRows.add(new ClassMemberHistory(
                    Ids.newId(),
                    classId,
                    userId,
                    ClassMemberHistoryAction.JOIN,
                    null,
                    actorId,
                    null,
                    occurredAt));
            assignedClasses.add(lockedById.get(classId));
        }

        if (!newMembers.isEmpty()) {
            classMemberRepository.saveAll(newMembers);
            classMemberRepository.flush();
            classMemberHistoryRepository.saveAll(historyRows);
            classMemberHistoryRepository.flush();

            for (ClassRoom classRoom : assignedClasses) {
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("addedUserIds", List.of(userId.toString()));
                after.put("addedCount", 1);
                auditRecorder.record(AuditEvent.builder(MODULE, "CLASS_MEMBER_ADDED")
                        .target(TARGET_TYPE_CLASS, classRoom.getId().toString(), classRoom.getCode())
                        .after(after)
                        .build());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findUserIdsInClass(UUID classId) {
        if (classId == null) {
            return Set.of();
        }
        return Set.copyOf(classMemberRepository.findActiveUserIdsByClassId(classId));
    }

    @Override
    @Transactional(readOnly = true)
    public UserClassesResult getUserClasses(UUID userId) {
        UserClassesResponse response = classService.getUserClasses(userId);
        List<CurrentClass> currentClasses = response.currentClasses().stream()
                .map(c -> new CurrentClass(
                        c.classId(),
                        c.classCode(),
                        c.className(),
                        c.classStatus().name(),
                        c.joinedAt(),
                        c.addedBy(),
                        c.addedByUsername()))
                .toList();
        List<ClassHistoryEntry> history = response.history().stream()
                .map(h -> new ClassHistoryEntry(
                        h.id(),
                        h.classId(),
                        h.classCode(),
                        h.className(),
                        h.action().name(),
                        h.relatedClassId(),
                        h.relatedClassCode(),
                        h.relatedClassName(),
                        h.actorId(),
                        h.actorUsername(),
                        h.reason(),
                        h.occurredAt()))
                .toList();
        return new UserClassesResult(currentClasses, history);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ClassLookupInfo> findClassesByNormalizedCodes(Collection<String> normalizedCodes) {
        if (normalizedCodes == null || normalizedCodes.isEmpty()) {
            return Map.of();
        }
        Set<String> lookupKeys = new LinkedHashSet<>();
        for (String code : normalizedCodes) {
            if (code != null && !code.isBlank()) {
                lookupKeys.add(code.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (lookupKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, ClassLookupInfo> result = new HashMap<>();
        for (ClassRoom classRoom : classRoomRepository.findAllByCodeNormalizedInAndDeletedAtIsNull(lookupKeys)) {
            result.put(
                    classRoom.getCodeNormalized(),
                    new ClassLookupInfo(
                            classRoom.getId(),
                            classRoom.getCode(),
                            classRoom.getCodeNormalized(),
                            classRoom.getStatus() == ClassStatus.ACTIVE));
        }
        return Map.copyOf(result);
    }
}
