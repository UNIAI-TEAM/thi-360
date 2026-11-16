package vn.unicom.thi360.catalog.spi;

import java.util.UUID;

/** Một liên kết từ giá trị nhóm cây tới giá trị của nhóm được khai báo trong {@code catalog_group_ref}. */
public record CatalogItemRefView(
        UUID refItemId,
        String refGroupCode,
        String refItemCode,
        String refNameVi,
        String refNameEn,
        CatalogStatus refStatus
) {
}
