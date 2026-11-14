package vn.unicom.thi360.classroom.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.classroom.domain.ClassRoom;

public interface ClassRoomRepository extends JpaRepository<ClassRoom, UUID> {

    Optional<ClassRoom> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClassRoom c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<ClassRoom> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    boolean existsByCodeNormalizedAndDeletedAtIsNull(String codeNormalized);

    boolean existsByCodeNormalizedAndDeletedAtIsNullAndIdNot(String codeNormalized, UUID id);

    List<ClassRoom> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    Optional<ClassRoom> findByCodeNormalizedAndDeletedAtIsNull(String codeNormalized);
}
