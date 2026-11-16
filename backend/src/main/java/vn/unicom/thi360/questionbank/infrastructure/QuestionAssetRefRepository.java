package vn.unicom.thi360.questionbank.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.questionbank.domain.QuestionAssetRef;

public interface QuestionAssetRefRepository extends JpaRepository<QuestionAssetRef, QuestionAssetRef.PK> {

    /** Tài nguyên mà một phiên bản câu dùng (đọc lại khi phục vụ phiên bản). */
    List<QuestionAssetRef> findByQuestionIdAndVersionNo(UUID questionId, Integer versionNo);

    /** Tài nguyên của nhiều phiên bản (ví dụ bộ câu + câu con ở cùng version_no). */
    List<QuestionAssetRef> findByQuestionIdInAndVersionNo(Collection<UUID> questionIds, Integer versionNo);

    /** Lưu tại chỗ một phiên bản Nháp thay thế các ref của chính phiên bản đó (model note 3). */
    @Modifying
    @Query("delete from QuestionAssetRef r where r.questionId = :questionId and r.versionNo = :versionNo")
    void deleteByQuestionIdAndVersionNo(@Param("questionId") UUID questionId, @Param("versionNo") Integer versionNo);
}
