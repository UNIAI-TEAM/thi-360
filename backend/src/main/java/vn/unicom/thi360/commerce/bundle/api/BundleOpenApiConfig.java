package vn.unicom.thi360.commerce.bundle.api;

import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.unicom.thi360.commerce.storefront.api.BundleDetailItem;

/**
 * OpenAPI 3.1 has no {@code nullable}: object {@code $ref} fields on bundle DTOs that can be {@code null}
 * must be emitted as {@code anyOf: [{$ref}, {type: null}]} so typed clients generate {@code | null}.
 */
@Configuration
public class BundleOpenApiConfig {

    private static final List<String> TARGET_SCHEMAS = List.of(
            BundleItemView.class.getSimpleName(),
            BundleCandidateResponse.class.getSimpleName(),
            BundleDetailItem.class.getSimpleName());

    private static final Set<String> NULLABLE_REFS = Set.of(
            "subjectName", "questionCount", "durationMinutes", "maxPoints");

    @Bean
    public OpenApiCustomizer bundleNullableRefs() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            for (String schemaName : TARGET_SCHEMAS) {
                Schema<?> schema = schemas.get(schemaName);
                if (schema != null && schema.getProperties() != null) {
                    allowNull(schema.getProperties());
                }
            }
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
