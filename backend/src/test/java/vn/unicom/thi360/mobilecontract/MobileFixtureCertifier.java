package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.test.web.servlet.MvcResult;

public final class MobileFixtureCertifier {

    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[\\d+]");

    private final ObjectMapper objectMapper;
    private final Set<String> certified = new TreeSet<>();

    public MobileFixtureCertifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void reset() {
        certified.clear();
    }

    public void certify(
            String relativePath, int expectedStatus, MvcResult actualResult, Set<String> dynamicFields)
            throws Exception {
        certify(
                relativePath,
                expectedStatus,
                actualResult.getResponse().getStatus(),
                actualResult.getResponse().getContentAsString(StandardCharsets.UTF_8),
                dynamicFields);
    }

    public void certify(
            String relativePath,
            int expectedStatus,
            int actualStatus,
            String actualBody,
            Set<String> dynamicFields)
            throws Exception {
        certified.add(relativePath);
        assertThat(actualStatus)
                .as("%s status (body=%s)", relativePath, actualBody)
                .isEqualTo(expectedStatus);

        Path fixtureFile = contractsMobileDir().resolve(relativePath);
        assertThat(fixtureFile).as("fixture exists: %s", relativePath).isRegularFile();

        JsonNode expected = objectMapper.readTree(Files.readString(fixtureFile, StandardCharsets.UTF_8));
        JsonNode actual = objectMapper.readTree(actualBody);
        assertNodesMatch(relativePath, "$", expected, actual, dynamicFields);
    }

    public void assertCertifiedExactly(Set<String> expectedFixtures) {
        assertThat(certified)
                .as("certified fixtures must match declared FIXTURES set")
                .containsExactlyInAnyOrderElementsOf(expectedFixtures);
    }

    public static void assertNodesMatch(
            String fixture, String path, JsonNode expected, JsonNode actual, Set<String> dynamicFields) {
        assertThat(actual.getNodeType())
                .as("%s at %s node type (expected=%s actual=%s)", fixture, path, expected, actual)
                .isEqualTo(expected.getNodeType());

        if (expected.isObject()) {
            Set<String> expectedKeys = fieldNames(expected);
            Set<String> actualKeys = fieldNames(actual);
            assertThat(actualKeys)
                    .as("%s at %s keys (expected=%s actual=%s)", fixture, path, expected, actual)
                    .containsExactlyInAnyOrderElementsOf(expectedKeys);
            for (String key : expectedKeys) {
                JsonNode expChild = expected.get(key);
                JsonNode actChild = actual.get(key);
                String childPath = path + "." + key;
                if (isDynamic(childPath, key, dynamicFields)) {
                    assertThat(actChild.getNodeType())
                            .as("%s at %s dynamic field type (expected=%s actual=%s)", fixture, childPath, expChild, actChild)
                            .isEqualTo(expChild.getNodeType());
                    if (actChild.isTextual()) {
                        assertThat(actChild.asText()).as("%s at %s non-blank", fixture, childPath).isNotBlank();
                    } else if (actChild.isNumber() && !"seed".equals(key)) {
                        assertThat(actChild.asLong()).as("%s at %s positive", fixture, childPath).isPositive();
                    }
                } else if ("fieldErrors".equals(key) && expChild.isArray() && actChild.isArray()) {
                    assertThat(normalizeFieldErrors(actChild))
                            .as("%s.fieldErrors", fixture)
                            .containsExactlyElementsOf(normalizeFieldErrors(expChild));
                } else {
                    assertNodesMatch(fixture, childPath, expChild, actChild, dynamicFields);
                }
            }
        } else if (expected.isArray()) {
            assertThat(actual.size()).as("%s at %s array size", fixture, path).isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                assertNodesMatch(fixture, path + "[" + i + "]", expected.get(i), actual.get(i), dynamicFields);
            }
        } else {
            assertThat(actual).as("%s at %s value", fixture, path).isEqualTo(expected);
        }
    }

    public static Path contractsMobileDir() {
        Path fromBackend = Path.of("..", "contracts", "mobile").normalize();
        if (Files.isDirectory(fromBackend)) {
            return fromBackend;
        }
        Path fromRoot = Path.of("contracts", "mobile").normalize();
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException("Could not locate contracts/mobile directory");
    }

    private static boolean isDynamic(String fullPath, String key, Set<String> dynamicFields) {
        if (dynamicFields.contains(key) || dynamicFields.contains(fullPath)) {
            return true;
        }
        String wildcardPath = ARRAY_INDEX_PATTERN.matcher(fullPath).replaceAll("[*]");
        return dynamicFields.contains(wildcardPath);
    }

    private static List<String> normalizeFieldErrors(JsonNode arrayNode) {
        List<String> tuples = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            tuples.add(item.path("field").asText()
                    + "|" + item.path("code").asText()
                    + "|" + item.path("message").asText());
        }
        tuples.sort(Comparator.naturalOrder());
        return tuples;
    }

    private static Set<String> fieldNames(JsonNode objectNode) {
        Set<String> keys = new LinkedHashSet<>();
        Iterator<String> it = objectNode.fieldNames();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        return keys;
    }
}
