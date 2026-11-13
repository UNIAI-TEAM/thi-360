package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import vn.unicom.thi360.catalog.spi.CatalogGroupType;

@Converter(autoApply = true)
public class CatalogGroupTypeConverter implements AttributeConverter<CatalogGroupType, Short> {

    @Override
    public Short convertToDatabaseColumn(CatalogGroupType attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public CatalogGroupType convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : CatalogGroupType.fromCode(dbData);
    }
}
