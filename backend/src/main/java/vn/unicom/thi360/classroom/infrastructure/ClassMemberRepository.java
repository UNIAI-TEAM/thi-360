package vn.unicom.thi360.classroom.infrastructure;

import java.util.Collection;
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

    @Query("""
            SELECT cm.id.userId AS userId, c.id AS classId, c.code AS code, c.name AS name
            FROM ClassMember cm
            JOIN ClassRoom c ON c.id = cm.id.classId
            WHERE cm.id.userId IN :userIds
              AND c.deletedAt IS NULL
            ORDER BY c.codeNormalized ASC
            """)
    List<UserClassRow> findCurrentClassRowsByUserIds(@Param("userIds") Collection<UUID> userIds);

    interface UserClassRow {
        UUID getUserId();

        UUID getClassId();

        String getCode();

        String getName();
    }
}
