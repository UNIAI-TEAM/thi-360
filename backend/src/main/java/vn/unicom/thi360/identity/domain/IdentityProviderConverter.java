package vn.unicom.thi360.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IdentityProviderConverter implements AttributeConverter<IdentityProvider, Short> {

    @Override
    public Short convertToDatabaseColumn(IdentityProvider attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public IdentityProvider convertToEntityAttribute(Short dbData) {
        return dbData == null ? null : IdentityProvider.fromCode(dbData);
    }
}
