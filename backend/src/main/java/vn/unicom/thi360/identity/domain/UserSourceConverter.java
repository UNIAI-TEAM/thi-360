package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserSourceConverter implements AttributeConverter<UserSource, Short> {

    @Override
    public Short convertToDatabaseColumn(UserSource attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public UserSource convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : UserSource.fromCode(dbData);
    }
}
