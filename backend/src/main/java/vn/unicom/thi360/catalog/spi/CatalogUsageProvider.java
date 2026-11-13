package vn.unicom.thi360.catalog.spi;

import java.util.List;
import java.util.UUID;

public interface CatalogUsageProvider {

    String source();

    List<CatalogUsage> findUsages(String groupCode, UUID itemId);

    default boolean isCombinationVersionReferenced(UUID itemId, UUID versionId) {
        if (itemId == null) {
            return false;
        }
        List<CatalogUsage> usages = findUsages("SUBJECT_COMBINATION", itemId);
        if (usages == null) {
            return false;
        }
        for (CatalogUsage usage : usages) {
            if (usage != null && usage.count() > 0L) {
                return true;
            }
        }
        return false;
    }
}
