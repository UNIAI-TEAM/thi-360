package vn.unicom.thi360.catalog.spi;

import java.util.UUID;

public record CatalogItemView(
        UUID id,
        UUID groupId,
        String groupCode,
        String code,
        int value,
        String nameVi,
        String nameEn,
        CatalogStatus status,
        CatalogStatus effectiveStatus,
        boolean system,
        UUID parentId,
        short depth,
        String path
) {
}
