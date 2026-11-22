package vn.unicom.thi360.attempt.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.unicom.thi360.attempt.domain.AttemptAudioPlay;

@Repository
public interface AttemptAudioPlayRepository extends JpaRepository<AttemptAudioPlay, UUID> {

    Optional<AttemptAudioPlay> findByAttemptIdAndAssetId(UUID attemptId, UUID assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AttemptAudioPlay p where p.attemptId = :attemptId and p.assetId = :assetId")
    Optional<AttemptAudioPlay> findByAttemptIdAndAssetIdForUpdate(
            @Param("attemptId") UUID attemptId, @Param("assetId") UUID assetId);

    List<AttemptAudioPlay> findByAttemptIdOrderByAssetIdAsc(UUID attemptId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AttemptAudioPlay p where p.attemptId = :attemptId and p.partId in :partIds order by p.assetId asc")
    List<AttemptAudioPlay> findByAttemptIdAndPartIdInOrderByAssetIdAscForUpdate(
            @Param("attemptId") UUID attemptId, @Param("partIds") Collection<UUID> partIds);

    boolean existsByAttemptIdAndAssetId(UUID attemptId, UUID assetId);
}
