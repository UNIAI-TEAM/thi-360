package vn.unicom.thi360.classroom.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;
import vn.unicom.thi360.classroom.domain.ClassMemberHistoryAction;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberHistoryRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.ids.Ids;

@Service
public class ClassMembershipPortImpl implements ClassMembershipPort {

    private static final String ERROR_CLASS_INACTIVE = "CLASS_INACTIVE";

    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassMemberHistoryRepository classMemberHistoryRepository;

    public ClassMembershipPortImpl(
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository) {
        this.classRoomRepository = classRoomRepository;
        this.classMemberRepository = classMemberRepository;
        this.classMemberHistoryRepository = classMemberHistoryRepository;
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
                        new FieldError("classIds", "INVALID", "ID lớp học không được để trống")));
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
                        new FieldError("classIds", "INVALID", "ID lớp học không được để trống")));
            }
            uniqueIds.add(classId);
        }

        List<UUID> sortedIds = uniqueIds.stream().sorted().toList();
        for (UUID classId : sortedIds) {
            ClassRoom locked = classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            if (locked.getStatus() == ClassStatus.INACTIVE) {
                throw new ConflictException(ERROR_CLASS_INACTIVE);
            }
        }

        List<ClassMember> newMembers = new ArrayList<>();
        List<ClassMemberHistory> historyRows = new ArrayList<>();
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
        }

        if (!newMembers.isEmpty()) {
            classMemberRepository.saveAll(newMembers);
            classMemberRepository.flush();
            classMemberHistoryRepository.saveAll(historyRows);
            classMemberHistoryRepository.flush();
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
}
