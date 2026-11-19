package vn.unicom.thi360.commerce.entitlement.access.api;

import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 has no {@code nullable}: a property that is a {@code $ref} and can be JSON {@code null} must be a union of
 * the reference and {@code null}, or the generated client types it without {@code null}. {@code @Schema(types = ...)}
 * covers the scalar and enum fields of {@link MyExamCard}; this customizer covers its three object fields, which are
 * written as {@code anyOf: [{$ref}, {type: null}]} (the description moves to the union).
 */
@Configuration
public class MyExamsOpenApiConfig {

    private static final Set<String> NULLABLE_REFS = Set.of("scopeLabel", "questionCount", "durationMinutes");

    @Bean
    public OpenApiCustomizer myExamCardNullableRefs() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> card = openApi.getComponents().getSchemas().get(MyExamCard.class.getSimpleName());
            if (card == null || card.getProperties() == null) {
                return;
            }
            allowNull(card.getProperties());
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void allowNull(Map<String, Schema> properties) {
        for (String name : NULLABLE_REFS) {
            Schema original = properties.get(name);
            if (original == null || original.get$ref() == null) {
                continue;
            }
            Schema union = new JsonSchema();
            union.setDescription(original.getDescription());
            original.setDescription(null);
            union.addAnyOfItem(original);
            union.addAnyOfItem(new JsonSchema().types(Set.of("null")));
            properties.put(name, union);
        }
    }
}
