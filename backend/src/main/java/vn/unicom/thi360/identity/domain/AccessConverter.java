package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import vn.unicom.thi360.shared.security.Access;

@Converter(autoApply = true)
public class AccessConverter implements AttributeConverter<Access, Short> {

    @Override
    public Short convertToDatabaseColumn(Access attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public Access convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : Access.fromCode(dbData);
    }
}
