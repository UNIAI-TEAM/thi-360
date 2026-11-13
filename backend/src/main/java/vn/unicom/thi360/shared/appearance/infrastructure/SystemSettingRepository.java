package vn.unicom.thi360.shared.appearance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.unicom.thi360.shared.appearance.domain.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
