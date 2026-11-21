package vn.unicom.thi360.commerce.reconciliation.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.commerce.reconciliation.domain.ReconciliationReport;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, UUID> {

    Optional<ReconciliationReport> findByReportDate(LocalDate reportDate);

    Optional<ReconciliationReport> findTopByOrderByReportDateDesc();
}
