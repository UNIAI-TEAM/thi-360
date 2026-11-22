package vn.unicom.thi360.classroom.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ClassroomLookupPort {

    Optional<ClassroomInfo> findById(UUID classId);

    Optional<ClassroomInfo> lockActiveOrInactiveById(UUID classId);

    Map<UUID, ClassroomInfo> findByIds(Collection<UUID> classIds);

    List<UUID> findActiveMemberUserIds(UUID classId);
}
