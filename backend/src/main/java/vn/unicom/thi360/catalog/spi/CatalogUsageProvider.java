package vn.unicom.thi360.catalog.spi;

import java.util.List;
import java.util.UUID;

public interface CatalogUsageProvider {

    String source();

    List<CatalogUsage> findUsages(String groupCode, UUID itemId);
}
