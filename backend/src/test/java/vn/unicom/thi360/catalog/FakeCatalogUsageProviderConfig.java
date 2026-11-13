package vn.unicom.thi360.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

            @Override
            public boolean isCombinationVersionReferenced(UUID itemId, UUID versionId) {
                return registry.isCombinationVersionReferenced(itemId, versionId);
            }
        };
    }

    public static class FakeCatalogUsageRegistry {

        private final Map<UUID, List<CatalogUsage>> usagesByItemId = new ConcurrentHashMap<>();
        private final Map<UUID, Set<UUID>> referencedVersionsByItemId = new ConcurrentHashMap<>();

        public void setUsages(UUID itemId, List<CatalogUsage> usages) {
            if (usages == null || usages.isEmpty()) {
                usagesByItemId.remove(itemId);
            } else {
                usagesByItemId.put(itemId, List.copyOf(usages));
            }
        }

        public void setReferencedVersions(UUID itemId, Set<UUID> versionIds) {
            if (versionIds == null) {
                referencedVersionsByItemId.remove(itemId);
            } else {
                referencedVersionsByItemId.put(itemId, Set.copyOf(versionIds));
            }
        }

        public void clear() {
            usagesByItemId.clear();
            referencedVersionsByItemId.clear();
        }

        public List<CatalogUsage> findUsages(UUID itemId) {
            return usagesByItemId.getOrDefault(itemId, List.of());
        }

        public boolean isCombinationVersionReferenced(UUID itemId, UUID versionId) {
            Set<UUID> trackedVersions = referencedVersionsByItemId.get(itemId);
            if (trackedVersions != null) {
                return versionId != null && trackedVersions.contains(versionId);
            }
            return !findUsages(itemId).isEmpty();
        }
    }
}
