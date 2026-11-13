package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;

@Converter(autoApply = true)
public class CatalogGroupKindConverter implements AttributeConverter<CatalogGroupKind, Short> {

    @Override
    public Short convertToDatabaseColumn(CatalogGroupKind attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public CatalogGroupKind convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : CatalogGroupKind.fromCode(dbData);
    }
}
