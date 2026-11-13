package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleStatusConverter implements AttributeConverter<RoleStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(RoleStatus attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public RoleStatus convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : RoleStatus.fromCode(dbData);
    }
}
