package vn.unicom.thi360.catalog.spi;

import java.util.UUID;

public record CatalogCombinationSubjectView(
        UUID itemId,
        String code,
        String nameVi,
        String nameEn
) {
}
