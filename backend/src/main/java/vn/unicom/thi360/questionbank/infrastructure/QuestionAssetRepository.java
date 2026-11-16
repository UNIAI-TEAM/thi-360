package vn.unicom.thi360.questionbank.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;

public interface QuestionAssetRepository extends JpaRepository<QuestionAsset, UUID> {
}
