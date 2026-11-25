package vn.unicom.thi360.commerce.storefront.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.commerce.pricing.PriceCalculator;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters.CatalogKind;
import vn.unicom.thi360.commerce.storefront.api.StorefrontFilters.KindFilter;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.Count;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.shared.web.AppMessages;

class StorefrontQueryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void filtersPopulateCatalogKindsFromActiveExamCatalogInOrderWhileKeepingKindsNonZero() {
        StorefrontProductRepository repository = mock(StorefrontProductRepository.class);
        CatalogLookup catalog = mock(CatalogLookup.class);
        StorefrontQueryService service = new StorefrontQueryService(
                repository,
                catalog,
                mock(PriceCalculator.class),
                mock(PublishedExamPort.class),
                objectMapper,
                mock(AppMessages.class));

        UUID groupId = UUID.randomUUID();
        when(catalog.listGroupItems("EXAM")).thenReturn(List.of(
                examItem(groupId, 1, "HSA", "Đánh giá năng lực ĐHQG Hà Nội (HSA)", "VNU Hanoi (HSA)", CatalogStatus.ACTIVE),
                examItem(groupId, 2, "QDA", "Đánh giá năng lực Bộ Quốc phòng (QDA)", "MOD (QDA)", CatalogStatus.ACTIVE),
                examItem(groupId, 3, "OLD", "Kỳ thi ngừng dùng", "Retired exam", CatalogStatus.INACTIVE),
                examItem(groupId, 4, "HSCA", "Đánh giá năng lực H-SCA", "H-SCA assessment", CatalogStatus.ACTIVE),
                examItem(groupId, 5, "VSAT", "Đánh giá năng lực V-SAT", "V-SAT assessment", CatalogStatus.ACTIVE)));
        when(catalog.listGroupItems("SUBJECT")).thenReturn(List.of());
        when(repository.countByKind()).thenReturn(List.of(new Count("HSA", 3), new Count("OLD", 1)));
        when(repository.countByScope()).thenReturn(List.of(new Count("FULL", 3)));
        when(repository.countBySubject()).thenReturn(List.of());
        when(repository.countByPart()).thenReturn(List.of());

        StorefrontFilters filters = service.filters();

        assertThat(filters.catalogKinds()).containsExactly(
                new CatalogKind("HSA", "Đánh giá năng lực ĐHQG Hà Nội (HSA)", "VNU Hanoi (HSA)"),
                new CatalogKind("QDA", "Đánh giá năng lực Bộ Quốc phòng (QDA)", "MOD (QDA)"),
                new CatalogKind("HSCA", "Đánh giá năng lực H-SCA", "H-SCA assessment"),
                new CatalogKind("VSAT", "Đánh giá năng lực V-SAT", "V-SAT assessment"));
        assertThat(filters.kinds()).containsExactly(
                new KindFilter("HSA", "Đánh giá năng lực ĐHQG Hà Nội (HSA)", "VNU Hanoi (HSA)", 3),
                new KindFilter("OLD", "Kỳ thi ngừng dùng", "Retired exam", 1));
    }

    @Test
    void openApiJsonMatchesSpringDocStorefrontFiltersAndCatalogKindSchemas() throws Exception {
        Path openApiPath = resolveWorkspaceFile(Path.of("packages", "api-client", "openapi.json"));
        String content = Files.readString(openApiPath, StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("\r");

        JsonNode root = objectMapper.readTree(content);
        JsonNode schemas = root.path("components").path("schemas");
        assertKeysSorted(schemas, "$.components.schemas");

        ModelConverters converters = new ModelConverters();
        converters.addConverter(new ModelResolver(Json31.mapper()).openapi31(true));
        ResolvedSchema resolved = converters.resolveAsResolvedSchema(new AnnotatedType(StorefrontFilters.class));

        JsonNode expectedStorefrontFilters = sortKeys(
                objectMapper.readTree(Json31.pretty(resolved.schema)));
        JsonNode expectedCatalogKind = sortKeys(
                objectMapper.readTree(Json31.pretty(resolved.referencedSchemas.get("CatalogKind"))));

        assertThat(schemas.path("CatalogKind")).isEqualTo(expectedCatalogKind);
        assertThat(schemas.path("StorefrontFilters")).isEqualTo(expectedStorefrontFilters);
    }

    private static CatalogItemView examItem(
            UUID groupId, int value, String code, String nameVi, String nameEn, CatalogStatus status) {
        UUID id = UUID.randomUUID();
        return new CatalogItemView(
                id,
                groupId,
                "EXAM",
                code,
                code.toLowerCase(),
                value,
                nameVi,
                nameEn,
                status,
                status,
                true,
                null,
                (short) 1,
                id.toString());
    }

    private static Path resolveWorkspaceFile(Path relative) throws IOException {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path dir = cwd; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Cannot locate " + relative + " from " + cwd);
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), sortKeys(entry.getValue())));
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode(node.size());
            node.forEach(element -> sorted.add(sortKeys(element)));
            return sorted;
        }
        return node;
    }

    private static void assertKeysSorted(JsonNode node, String path) {
        if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("Keys sorted at %s", path).containsExactlyElementsOf(keys.stream().sorted().toList());
            node.properties().forEach(entry -> assertKeysSorted(entry.getValue(), path + "." + entry.getKey()));
        }
    }
}
