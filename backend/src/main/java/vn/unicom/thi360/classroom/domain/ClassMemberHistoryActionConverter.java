package vn.unicom.thi360.classroom.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ClassMemberHistoryActionConverter implements AttributeConverter<ClassMemberHistoryAction, Short> {

    @Override
    public Short convertToDatabaseColumn(ClassMemberHistoryAction attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ClassMemberHistoryAction convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : ClassMemberHistoryAction.fromCode(dbData);
    }
}
