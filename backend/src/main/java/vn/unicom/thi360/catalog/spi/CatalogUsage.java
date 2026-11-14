package vn.unicom.thi360.catalog.spi;

import java.util.List;

public record CatalogUsage(
        String source,
        String objectType,
        long count,
        boolean blocking,
        List<String> sampleLabels
) {
    public CatalogUsage {
        sampleLabels = sampleLabels == null ? List.of() : List.copyOf(sampleLabels);
    }
}
