package vn.unicom.thi360.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.attempt.testsupport.E2eAudioFaultController;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.AuthCookieService;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.testapi.MutableClock;
import vn.unicom.thi360.shared.testapi.TestApiPaths;
import vn.unicom.thi360.shared.testapi.TestClockController;

/**
 * AC-11: Verifies test-only APIs and E2E beans are absent without the {@code e2e} profile,
 * {@code /api/v1/test/**} always returns 404 {@code NOT_FOUND} regardless of credentials,
 * {@code E2eProfileGuard} blocks {@code e2e + prod}, and OpenAPI omits {@code /api/v1/test/**}
 * and {@code /mock-vnpay**}.
 */
class TestApiProfileGuardIT extends AbstractIntegrationTest {

    private static final List<String> TEST_PATHS = List.of(
            "/api/v1/test/clock",
            "/api/v1/test/mail",
            "/api/v1/test/audio-faults/00000000-0000-0000-0000-000000000000",
            "/api/v1/test/anything",
            "/api/v1/test");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allTestRoutesReturn404NotFoundAcrossMethodsAndCredentials() throws Exception {
        Cookie staleAccess = new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "stale.session.token");
        Cookie staleRefresh = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE, "stale.refresh.token");
        String[] badAuthorizations = {
                "Bearer not-a-jwt",
                "Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln",
                "Bearer ",
                "Basic Zm9vOmJhcg=="
        };

        for (String path : TEST_PATHS) {
            // Anonymous
            expect404NotFound(get(path));
            expect404NotFound(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"));
            expect404NotFound(delete(path));

            // Bad Bearer / Basic credentials
            for (String authorization : badAuthorizations) {
                expect404NotFound(get(path).header(HttpHeaders.AUTHORIZATION, authorization));
                expect404NotFound(post(path).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"advanceSeconds\":3600}"));
                expect404NotFound(delete(path).header(HttpHeaders.AUTHORIZATION, authorization));
            }

            // Stale session cookie without XSRF token
            expect404NotFound(get(path).cookie(staleAccess, staleRefresh));
            expect404NotFound(post(path).cookie(staleAccess, staleRefresh)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"));
            expect404NotFound(delete(path).cookie(staleAccess, staleRefresh));

            // Valid student / user principal
            expect404NotFound(get(path).with(asUser()));
            expect404NotFound(post(path).with(asUser()).contentType(MediaType.APPLICATION_JSON).content("{}"));
            expect404NotFound(delete(path).with(asUser()));

            // Valid admin / platform admin principal
            expect404NotFound(get(path).with(asUser(Permissions.EXAM_VIEW)));
            expect404NotFound(post(path).with(asUser(Permissions.EXAM_VIEW))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"));
            expect404NotFound(delete(path).with(asUser(Permissions.EXAM_VIEW)));
            expect404NotFound(get(path).with(asPlatformAdmin()));
            expect404NotFound(post(path).with(asPlatformAdmin()).contentType(MediaType.APPLICATION_JSON).content("{}"));
            expect404NotFound(delete(path).with(asPlatformAdmin()));
        }
    }

    @Test
    void noTestApiMappingsOrE2eBeansExistWithoutE2eProfile() throws Exception {
        Map<String, RequestMappingHandlerMapping> mappings =
                context.getBeansOfType(RequestMappingHandlerMapping.class);
        assertThat(mappings).isNotEmpty();
        for (RequestMappingHandlerMapping handlerMapping : mappings.values()) {
            for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
                assertThat(info.getPatternValues())
                        .as("mapping %s", info)
                        .noneMatch(TestApiPaths::matches);
            }
        }

        Class<?> mailTestControllerClass = Class.forName("vn.unicom.thi360.shared.mail.MailTestController");
        assertThat(context.getBeanNamesForType(TestClockController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(mailTestControllerClass)).isEmpty();
        assertThat(context.getBeanNamesForType(E2eAudioFaultController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(MutableClock.class)).isEmpty();
        assertThat(context.getBean(Clock.class)).isNotInstanceOf(MutableClock.class);
    }

    @Test
    void e2eProfileGuardRejectsE2eCombinedWithProd() throws Exception {
        Class<?> e2eConfigClass = Class.forName("vn.unicom.thi360.shared.testapi.E2eConfig");

        for (String[] profiles : List.of(
                new String[] {"api", "worker", "e2e", "prod"},
                new String[] {"prod", "e2e"})) {
            runnerForProfiles(e2eConfigClass, profiles).run(ctx -> {
                assertThat(ctx).hasFailed();
                assertThat(ctx.getStartupFailure())
                        .isInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("'e2e'")
                        .hasStackTraceContaining("'prod'");
            });
        }

        runnerForProfiles(e2eConfigClass, "api", "worker", "e2e").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(Clock.class)).isInstanceOf(MutableClock.class);
        });
    }

    @Test
    void openApiDocsContainZeroTestOrMockVnpayPaths() throws Exception {
        String rawJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode paths = root.path("paths");
        assertThat(paths.isObject()).isTrue();

        List<String> allPaths = new ArrayList<>();
        paths.fieldNames().forEachRemaining(allPaths::add);
        assertThat(allPaths)
                .as("OpenAPI paths must be non-empty so path exclusion check is non-vacuous")
                .isNotEmpty()
                .contains("/api/v1/ping", "/api/v1/public/storefront/products");

        assertThat(allPaths)
                .noneMatch(p -> p.startsWith("/api/v1/test") || p.startsWith("/mock-vnpay"));
    }

    private void expect404NotFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private static ApplicationContextRunner runnerForProfiles(Class<?> e2eConfigClass, String... profiles) {
        return new ApplicationContextRunner()
                .withBean(KeycloakProperties.class, () -> new KeycloakProperties(
                        "http://localhost:8360",
                        "thi-360",
                        "thi360-api",
                        "s",
                        "master",
                        "PLATFORM_ADMIN",
                        "thi360-platform",
                        "s"))
                .withUserConfiguration(e2eConfigClass)
                .withInitializer(ctx -> {
                    MutablePropertySources sources = ctx.getEnvironment().getPropertySources();
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                    Map.of()));
                    sources.replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, Map.of()));
                    ctx.getEnvironment().setActiveProfiles(profiles);
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                });
    }
}
