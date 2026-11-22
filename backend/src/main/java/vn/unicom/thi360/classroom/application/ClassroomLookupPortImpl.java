package vn.unicom.thi360.classroom.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.classroom.spi.ClassroomInfo;
import vn.unicom.thi360.classroom.spi.ClassroomLookupPort;
import vn.unicom.thi360.identity.spi.UserDirectory;

@Service
public class ClassroomLookupPortImpl implements ClassroomLookupPort {

    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserDirectory userDirectory;

    public ClassroomLookupPortImpl(
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            UserDirectory userDirectory) {
        this.classRoomRepository = Objects.requireNonNull(classRoomRepository);
        this.classMemberRepository = Objects.requireNonNull(classMemberRepository);
        this.userDirectory = Objects.requireNonNull(userDirectory);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClassroomInfo> findById(UUID classId) {
        if (classId == null) {
            return Optional.empty();
        }
        return classRoomRepository.findByIdAndDeletedAtIsNull(classId).map(ClassroomLookupPortImpl::toInfo);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ClassroomInfo> lockActiveOrInactiveById(UUID classId) {
        if (classId == null) {
            return Optional.empty();
        }
        return classRoomRepository.findByIdAndDeletedAtIsNullForUpdate(classId).map(ClassroomLookupPortImpl::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ClassroomInfo> findByIds(Collection<UUID> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID id : classIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ClassroomInfo> map = new LinkedHashMap<>();
        for (ClassRoom room : classRoomRepository.findAllById(unique)) {
            map.put(room.getId(), toInfo(room));
        }
        return Map.copyOf(map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findActiveMemberUserIds(UUID classId) {
        if (classId == null) {
            return List.of();
        }
        List<UUID> rawUserIds = classMemberRepository.findActiveUserIdsByClassId(classId);
        if (rawUserIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserDirectory.UserDetailSummary> details = userDirectory.findDetailsByIds(rawUserIds);
        return rawUserIds.stream()
                .filter(uid -> {
                    UserDirectory.UserDetailSummary detail = details.get(uid);
                    return detail != null && detail.isAvailableForMembership();
                })
                .distinct()
                .sorted()
                .toList();
    }

    private static ClassroomInfo toInfo(ClassRoom room) {
        return new ClassroomInfo(
                room.getId(),
                room.getCode(),
                room.getName(),
                room.getDeletedAt() == null && room.getStatus() == ClassStatus.ACTIVE,
                room.getDeletedAt() != null);
    }
}
