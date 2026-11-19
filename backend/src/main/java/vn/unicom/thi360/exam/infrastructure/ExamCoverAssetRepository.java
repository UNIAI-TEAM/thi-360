package vn.unicom.thi360.exam.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.unicom.thi360.exam.domain.ExamCoverAsset;

public interface ExamCoverAssetRepository extends JpaRepository<ExamCoverAsset, UUID> {
    Optional<ExamCoverAsset> findByIdAndExamId(UUID id, UUID examId);

    /**
     * Locks the cover row of this exam that the cleanup has not claimed for deletion, so attaching it and the cleanup
     * sweep serialize on the same row; empty when there is no such cover or the sweep already took it. Use inside the
     * transaction that writes the exam.
     */
    @Query(value = "SELECT a.id FROM exam.exam_cover_asset a WHERE a.id = :id AND a.exam_id = :examId "
            + "AND a.delete_requested_at IS NULL FOR UPDATE", nativeQuery = true)
    Optional<UUID> lockAttachable(@Param("id") UUID id, @Param("examId") UUID examId);

    /** A cover that is attached again is in use: the next sweep must not count the time it was unused before. */
    @Modifying
    @Query(value = "UPDATE exam.exam_cover_asset SET orphaned_at = NULL WHERE id = :id", nativeQuery = true)
    int clearOrphaned(@Param("id") UUID id);
}
