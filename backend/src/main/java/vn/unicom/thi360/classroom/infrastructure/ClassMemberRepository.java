package vn.unicom.thi360.classroom.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberId;

public interface ClassMemberRepository extends JpaRepository<ClassMember, ClassMemberId> {

    boolean existsByIdClassIdAndIdUserId(UUID classId, UUID userId);

    Optional<ClassMember> findByIdClassIdAndIdUserId(UUID classId, UUID userId);

    List<ClassMember> findByIdClassIdOrderByJoinedAtDescIdUserIdAsc(UUID classId);

    List<ClassMember> findByIdUserIdOrderByIdClassIdAsc(UUID userId);

    @Query("""
            SELECT cm.id.userId
            FROM ClassMember cm
            JOIN ClassRoom c ON c.id = cm.id.classId
            WHERE cm.id.classId = :classId
              AND c.deletedAt IS NULL
            """)
    List<UUID> findActiveUserIdsByClassId(@Param("classId") UUID classId);
}
