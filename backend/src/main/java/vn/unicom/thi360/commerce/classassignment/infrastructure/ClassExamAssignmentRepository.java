package vn.unicom.thi360.commerce.classassignment.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.commerce.classassignment.domain.ClassExamAssignment;
import vn.unicom.thi360.commerce.classassignment.domain.ClassExamAssignmentStatus;

public interface ClassExamAssignmentRepository extends JpaRepository<ClassExamAssignment, UUID> {

    List<ClassExamAssignment> findByClassIdOrderByAssignedAtDescIdDesc(UUID classId);

    List<ClassExamAssignment> findByClassIdAndStatusAndAutoGrantNewMembersTrueOrderByAssignedAtAscIdAsc(
            UUID classId, ClassExamAssignmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ClassExamAssignment a WHERE a.classId = :classId AND a.status = :status ORDER BY a.id ASC")
    List<ClassExamAssignment> lockByClassIdAndStatus(
            @Param("classId") UUID classId,
            @Param("status") ClassExamAssignmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ClassExamAssignment a WHERE a.id = :id AND a.classId = :classId")
    Optional<ClassExamAssignment> lockByIdAndClassId(
            @Param("id") UUID id,
            @Param("classId") UUID classId);
}
