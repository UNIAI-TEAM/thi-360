package vn.unicom.thi360.shared.api;

import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 / JSON Schema: a property typed {@code ["string","null"]} only accepts null when {@code null}
 * is also an {@code enum} member. {@code @Schema(allowableValues = ...)} can only list strings, so this
 * customizer appends the null member to every top-level component property that is both nullable and an enum.
 */
@Configuration
public class OpenApiNullableEnumConfig {

    @Bean
    public OpenApiCustomizer nullableEnumCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(OpenApiNullableEnumConfig::allowNullInEnums);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void allowNullInEnums(Schema<?> schema) {
        if (schema.getProperties() == null) {
            return;
        }
        for (Schema property : schema.getProperties().values()) {
            if (property.getTypes() != null && property.getTypes().contains("null")
                    && property.getEnum() != null && !property.getEnum().contains(null)) {
                List values = new ArrayList<>(property.getEnum());
                values.add(null);
                property.setEnum(values);
            }
        }
    }
}
