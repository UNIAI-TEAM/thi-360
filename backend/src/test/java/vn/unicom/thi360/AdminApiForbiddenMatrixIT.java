package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.PermissionCatalog;
import vn.unicom.thi360.shared.security.RequirePermission;
import vn.unicom.thi360.shared.security.SecurityProperties;
import vn.unicom.thi360.shared.security.TestSecurity.SimulatedPrincipal;

/**
 * 403/401 matrix for every {@code /api/v1/admin/**} handler (UNI-2553, plan T16).
 *
 * <p>Endpoints are discovered at runtime from {@link RequestMappingHandlerMapping}; there is no
 * hand-written endpoint list. For every (HTTP method, pattern) the test calls the real filter chain
 * with
 *
 * <ol>
 *   <li>a user holding every catalogue permission except one the endpoint requires: 403 {@code FORBIDDEN};
 *   <li>a student principal ({@code student01}, role STUDENT, access EXAM): 403 {@code FORBIDDEN};
 *   <li>no credentials: 401 {@code UNAUTHENTICATED}.
 * </ol>
 *
 * <p>{@code @RequirePermission} is enforced by an aspect that runs after Spring MVC argument
 * binding and validation, so the requests carry a minimal valid body (only the {@code @NotNull},
 * {@code @NotBlank} and {@code @NotEmpty} members), required parameters, headers and a multipart
 * file. A 400 before the permission check is reported as a failure of the affected endpoint.
 */
class AdminApiForbiddenMatrixIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AdminApiForbiddenMatrixIT.class);

    private static final String ADMIN_PREFIX = "/api/v1/admin";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}:/]+)(?::[^}]*)?}");

    /**
     * Admin mappings that need authentication only (no {@code @RequirePermission}). Empty on purpose:
     * every current admin endpoint is permission-gated. Add an entry as {@code "METHOD pattern"} with
     * a comment explaining why the endpoint is open to any authenticated MANAGE user.
     */
    private static final Set<String> AUTHENTICATION_ONLY_ADMIN_MAPPINGS = Set.of();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private PermissionCatalog permissionCatalog;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    private record Endpoint(HttpMethod method, String pattern, HandlerMethod handler, List<String> requiredCodes) {

        String key() {
            return method + " " + pattern;
        }
    }

    @Test
    void userMissingTheRequiredPermissionGets403() throws Exception {
        List<Endpoint> endpoints = discoverEndpoints();
        List<String> failures = new ArrayList<>();
        int calls = 0;
        for (Endpoint endpoint : endpoints) {
            for (String missing : endpoint.requiredCodes()) {
                Set<String> granted = new TreeSet<>(permissionCatalog.allCodes());
                assertThat(granted.remove(missing))
                        .as("%s requires %s which is not in the permission catalogue", endpoint.key(), missing)
                        .isTrue();
                RequestPostProcessor user =
                        asUser(granted.toArray(String[]::new));
                calls++;
                check(failures, endpoint, "all permissions except " + missing, user, 403, "FORBIDDEN");
            }
        }
        report("every permission except the required one -> 403", endpoints.size(), calls);
        assertThat(failures).as("endpoints that did not answer 403 FORBIDDEN").isEmpty();
    }

    @Test
    void student01Gets403() throws Exception {
        List<Endpoint> endpoints = discoverEndpoints();
        RequestPostProcessor student = testSecuritySupport.apply(
                new SimulatedPrincipal(Ids.newId(), "student01", "STUDENT", false, List.of()));
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            check(failures, endpoint, "student01", student, 403, "FORBIDDEN");
        }
        report("student01 -> 403", endpoints.size(), endpoints.size());
        assertThat(failures).as("endpoints that did not answer 403 FORBIDDEN to student01").isEmpty();
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        List<Endpoint> endpoints = discoverEndpoints();
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            check(failures, endpoint, "no cookie", request -> request, 401, "UNAUTHENTICATED");
        }
        report("no cookie -> 401", endpoints.size(), endpoints.size());
        assertThat(failures).as("endpoints that did not answer 401 UNAUTHENTICATED without credentials").isEmpty();
    }

    // ---------------------------------------------------------------- discovery

    private List<Endpoint> discoverEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Set<String> adminPatterns = new LinkedHashSet<>();
            for (String pattern : info.getPatternValues()) {
                if (pattern.equals(ADMIN_PREFIX) || pattern.startsWith(ADMIN_PREFIX + "/")) {
                    adminPatterns.add(pattern);
                }
            }
            if (adminPatterns.isEmpty()) {
                continue;
            }
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                unresolved.add(adminPatterns + " -> " + handler.getShortLogMessage()
                        + ": mapping declares no HTTP method, cannot tell which methods it serves");
                continue;
            }
            List<String> required = requiredPermissions(handler);
            for (String pattern : adminPatterns) {
                for (var method : methods) {
                    Endpoint endpoint = new Endpoint(HttpMethod.valueOf(method.name()), pattern, handler, required);
                    if (required.isEmpty() && !AUTHENTICATION_ONLY_ADMIN_MAPPINGS.contains(endpoint.key())) {
                        unresolved.add(endpoint.key() + " -> " + handler.getShortLogMessage()
                                + ": no @RequirePermission on method or class, and not in the allow-list");
                        continue;
                    }
                    endpoints.add(endpoint);
                }
            }
        }
        endpoints.sort(Comparator.comparing(Endpoint::key));
        assertThat(unresolved)
                .as("admin mappings whose required permission cannot be determined")
                .isEmpty();
        assertThat(endpoints).as("admin endpoints discovered from RequestMappingHandlerMapping").isNotEmpty();
        return endpoints;
    }

    private static List<String> requiredPermissions(HandlerMethod handler) {
        RequirePermission onMethod =
                AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequirePermission.class);
        RequirePermission resolved = onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequirePermission.class);
        if (resolved == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (String code : resolved.value()) {
            if (code != null && !code.isBlank()) {
                codes.add(code.trim());
            }
        }
        return List.copyOf(codes);
    }

    private void report(String scenario, int endpoints, int calls) {
        String line = "AdminApiForbiddenMatrixIT: " + endpoints + " admin endpoints, " + calls
                + " calls, scenario: " + scenario;
        log.info(line);
        System.out.println(line);
        assertThat(endpoints).isGreaterThan(0);
    }

    // ---------------------------------------------------------------- calls

    private void check(
            List<String> failures,
            Endpoint endpoint,
            String scenario,
            RequestPostProcessor identity,
            int expectedStatus,
            String expectedCode)
            throws Exception {
        MvcResult result = mockMvc.perform(buildRequest(endpoint).with(identity)).andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String code = errorCode(body);
        if (status != expectedStatus || !expectedCode.equals(code)) {
            failures.add("%s [%s]: expected %d %s but got %d code=%s body=%s"
                    .formatted(endpoint.key(), scenario, expectedStatus, expectedCode, status, code, abbreviate(body)));
        }
    }

    private String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode code = objectMapper.readTree(body).get("code");
            return code == null ? null : code.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private MockHttpServletRequestBuilder buildRequest(Endpoint endpoint) throws Exception {
        HandlerMethod handler = endpoint.handler();
        DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();
        MethodParameter[] parameters = handler.getMethodParameters();
        for (MethodParameter parameter : parameters) {
            parameter.initParameterNameDiscovery(names);
        }

        boolean multipart = false;
        for (MethodParameter parameter : parameters) {
            if (MultipartFile.class.isAssignableFrom(parameter.getParameterType())
                    || parameter.hasParameterAnnotation(RequestPart.class)) {
                multipart = true;
            }
        }

        String url = expandPath(endpoint, parameters);
        MockHttpServletRequestBuilder builder =
                multipart ? multipart(endpoint.method(), url) : request(endpoint.method(), url);
        // A browser always sends Origin on writes; without it AdminOriginFilter answers 403 ORIGIN_INVALID
        // before authorization is reached, which would hide the status this matrix asserts.
        builder.header(HttpHeaders.ORIGIN, securityProperties.adminOrigins().getFirst());

        for (MethodParameter parameter : parameters) {
            RequestBody requestBody = parameter.getParameterAnnotation(RequestBody.class);
            RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
            RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
            RequestPart requestPart = parameter.getParameterAnnotation(RequestPart.class);
            if (requestBody != null) {
                if (requestBody.required()) {
                    builder.contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(
                                    sampleBody(endpoint, parameter.getParameterType())));
                }
            } else if (MultipartFile.class.isAssignableFrom(parameter.getParameterType()) || requestPart != null) {
                String partName = requestPart != null && !requestPart.name().isEmpty()
                        ? requestPart.name()
                        : requestParam != null && !requestParam.name().isEmpty()
                                ? requestParam.name()
                                : parameter.getParameterName();
                ((org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) builder)
                        .file(new MockMultipartFile(
                                partName, "sample.xlsx", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1}));
            } else if (requestParam != null && isRequired(requestParam.required(), requestParam.defaultValue())) {
                String name = requestParam.name().isEmpty() ? parameter.getParameterName() : requestParam.name();
                builder.param(name, scalarSample(parameter.getParameterType()));
            } else if (requestHeader != null && isRequired(requestHeader.required(), requestHeader.defaultValue())) {
                String name = requestHeader.name().isEmpty() ? parameter.getParameterName() : requestHeader.name();
                builder.header(name, scalarSample(parameter.getParameterType()));
            }
        }
        return builder;
    }

    private static boolean isRequired(boolean required, String defaultValue) {
        return required && ValueConstants.DEFAULT_NONE.equals(defaultValue);
    }

    private String expandPath(Endpoint endpoint, MethodParameter[] parameters) {
        Matcher matcher = PATH_VARIABLE.matcher(endpoint.pattern());
        StringBuilder url = new StringBuilder();
        while (matcher.find()) {
            String variable = matcher.group(1);
            MethodParameter owner = null;
            for (MethodParameter parameter : parameters) {
                PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
                if (pathVariable == null) {
                    continue;
                }
                String name = !pathVariable.name().isEmpty() ? pathVariable.name() : parameter.getParameterName();
                if (variable.equals(name)) {
                    owner = parameter;
                }
            }
            assertThat(owner)
                    .as("%s: no @PathVariable parameter for {%s}", endpoint.key(), variable)
                    .isNotNull();
            matcher.appendReplacement(url, Matcher.quoteReplacement(scalarSample(owner.getParameterType())));
        }
        matcher.appendTail(url);
        return url.toString();
    }

    // ---------------------------------------------------------------- sample data

    private static String scalarSample(Class<?> type) {
        if (type == UUID.class) {
            return UUID.randomUUID().toString();
        }
        if (type == String.class) {
            return "ab1";
        }
        if (type.isEnum()) {
            return ((Enum<?>) type.getEnumConstants()[0]).name();
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class
                || type == Short.class || type == short.class) {
            return "1";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true";
        }
        if (type == java.time.Instant.class) {
            return "2026-01-01T00:00:00Z";
        }
        throw new IllegalStateException("AdminApiForbiddenMatrixIT cannot build a sample value for " + type.getName()
                + "; extend scalarSample");
    }

    private JsonNode sampleBody(Endpoint endpoint, Class<?> bodyType) throws Exception {
        JsonNode body = sampleObject(bodyType, 0);
        Object instance = objectMapper.treeToValue(body, bodyType);
        Set<ConstraintViolation<Object>> violations = validator.validate(instance);
        assertThat(violations)
                .as("%s: generated sample for %s must pass bean validation so the permission check is reached",
                        endpoint.key(), bodyType.getSimpleName())
                .isEmpty();
        return body;
    }

    private ObjectNode sampleObject(Class<?> type, int depth) {
        if (depth > 4) {
            throw new IllegalStateException("Sample body nesting too deep for " + type.getName());
        }
        ObjectNode node = objectMapper.createObjectNode();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (field.isAnnotationPresent(NotNull.class)
                    || field.isAnnotationPresent(NotBlank.class)
                    || field.isAnnotationPresent(NotEmpty.class)) {
                node.set(field.getName(), sampleValue(field.getGenericType(), field, depth));
            }
        }
        return node;
    }

    private JsonNode sampleValue(Type type, Field source, int depth) {
        Class<?> raw = type instanceof ParameterizedType parameterized
                ? (Class<?>) parameterized.getRawType()
                : (Class<?>) type;
        if (raw == String.class) {
            Size size = source.getAnnotation(Size.class);
            int length = Math.max(3, size == null ? 0 : size.min());
            return objectMapper.getNodeFactory().textNode("ab1".repeat((length + 2) / 3).substring(0, length));
        }
        if (raw == UUID.class) {
            return objectMapper.getNodeFactory().textNode(UUID.randomUUID().toString());
        }
        if (raw == Integer.class || raw == int.class || raw == Long.class || raw == long.class
                || raw == Short.class || raw == short.class) {
            Min min = source.getAnnotation(Min.class);
            return objectMapper.getNodeFactory().numberNode(Math.max(1L, min == null ? 1L : min.value()));
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return objectMapper.getNodeFactory().booleanNode(true);
        }
        if (raw.isEnum()) {
            return objectMapper.getNodeFactory().textNode(((Enum<?>) raw.getEnumConstants()[0]).name());
        }
        if (Collection.class.isAssignableFrom(raw)) {
            ArrayNode array = objectMapper.createArrayNode();
            Size size = source.getAnnotation(Size.class);
            boolean needsElement = source.isAnnotationPresent(NotEmpty.class) || (size != null && size.min() > 0);
            if (needsElement) {
                Type element = ((ParameterizedType) type).getActualTypeArguments()[0];
                array.add(sampleValue(element, source, depth + 1));
            }
            return array;
        }
        if (Map.class.isAssignableFrom(raw)) {
            return objectMapper.createObjectNode();
        }
        return sampleObject(raw, depth + 1);
    }
}
