package vn.unicom.thi360.questionbank.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.questionbank.domain.Question;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Optional<Question> findByIdAndDeletedAtIsNull(UUID id);

    /** Khóa dòng câu (SELECT … FOR UPDATE) cho lệnh ghi / xóa (spec 4.4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findByIdForUpdate(@Param("id") UUID id);

    /** Câu con của một bộ, theo thứ tự trong bộ. */
    List<Question> findByParentIdAndDeletedAtIsNullOrderByPosition(UUID parentId);

    Optional<Question> findBySourceCodeNormalizedAndParentIdIsNullAndDeletedAtIsNull(
            String sourceCodeNormalized);

    /** Khóa hàng loạt (SELECT … FOR UPDATE) cho lệnh ghi loạt (spec 4.5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id in :ids")
    List<Question> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    /** Mọi câu con của một bộ, kể cả đã xóa mềm (dùng khi khôi phục phiên bản). */
    List<Question> findAllByParentId(UUID parentId);

    /** Người tạo đang có câu độc lập chưa xóa (mục lọc "Người tạo" của web admin). */
    @Query("select distinct q.createdBy from Question q"
            + " where q.deletedAt is null and q.parentId is null and q.createdBy is not null")
    List<UUID> findDistinctCreators();
}
