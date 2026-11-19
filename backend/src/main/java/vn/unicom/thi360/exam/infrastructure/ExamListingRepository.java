package vn.unicom.thi360.exam.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.exam.domain.ExamListing;

public interface ExamListingRepository extends JpaRepository<ExamListing, UUID> {
}
