package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public UserStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : UserStatus.fromCode(dbData);
    }
}
