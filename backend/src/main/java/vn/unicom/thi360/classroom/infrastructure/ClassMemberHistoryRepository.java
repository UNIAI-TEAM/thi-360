package vn.unicom.thi360.classroom.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;

public interface ClassMemberHistoryRepository extends JpaRepository<ClassMemberHistory, UUID> {
}
