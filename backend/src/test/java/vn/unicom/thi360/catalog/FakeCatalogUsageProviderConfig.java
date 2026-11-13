package vn.unicom.thi360.catalog;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.catalog.spi.CatalogUsageProvider;

@TestConfiguration(proxyBeanMethods = false)
public class FakeCatalogUsageProviderConfig {

    @Bean
    public FakeCatalogUsageRegistry fakeCatalogUsageRegistry() {
        return new FakeCatalogUsageRegistry();
    }

    @Bean
    public CatalogUsageProvider fakeExternalCatalogUsageProvider(FakeCatalogUsageRegistry registry) {
        return new CatalogUsageProvider() {
            @Override
            public String source() {
                return "Bài thi giả lập";
            }

            @Override
            public List<CatalogUsage> findUsages(String groupCode, UUID itemId) {
                return registry.findUsages(itemId);
            }
        };
    }

    public static class FakeCatalogUsageRegistry {

        private final Map<UUID, List<CatalogUsage>> usagesByItemId = new ConcurrentHashMap<>();

        public void setUsages(UUID itemId, List<CatalogUsage> usages) {
            if (usages == null || usages.isEmpty()) {
                usagesByItemId.remove(itemId);
            } else {
                usagesByItemId.put(itemId, List.copyOf(usages));
            }
        }

        public void clear() {
            usagesByItemId.clear();
        }

        public List<CatalogUsage> findUsages(UUID itemId) {
            return usagesByItemId.getOrDefault(itemId, List.of());
        }
    }
}
