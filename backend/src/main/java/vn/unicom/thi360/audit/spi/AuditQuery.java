package vn.unicom.thi360.audit.spi;

import java.time.Instant;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;

public interface AuditQuery {

    PageResponse<AuditEventView> findByTarget(String targetType, String targetId, PageQuery pageQuery);

    PageResponse<AuditEventView> findByTarget(
            String targetType,
            String targetId,
            Instant from,
            Instant to,
            PageQuery pageQuery);

    PageResponse<AuditEventView> query(AuditQueryCriteria criteria, PageQuery pageQuery);
}
