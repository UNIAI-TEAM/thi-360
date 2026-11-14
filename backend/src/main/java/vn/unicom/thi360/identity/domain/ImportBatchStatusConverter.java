package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ImportBatchStatusConverter implements AttributeConverter<ImportBatchStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ImportBatchStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ImportBatchStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ImportBatchStatus.fromCode(dbData);
    }
}
