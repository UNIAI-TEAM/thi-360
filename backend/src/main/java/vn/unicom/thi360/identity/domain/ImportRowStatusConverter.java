package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ImportRowStatusConverter implements AttributeConverter<ImportRowStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ImportRowStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ImportRowStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ImportRowStatus.fromCode(dbData);
    }
}
