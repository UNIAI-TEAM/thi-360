package vn.unicom.thi360.classroom.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ClassStatusConverter implements AttributeConverter<ClassStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ClassStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ClassStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ClassStatus.fromCode(dbData);
    }
}
