package vn.unicom.thi360.catalog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import vn.unicom.thi360.catalog.spi.CatalogStatus;

@Converter(autoApply = true)
public class CatalogStatusConverter implements AttributeConverter<CatalogStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(CatalogStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public CatalogStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : CatalogStatus.fromCode(dbData);
    }
}
