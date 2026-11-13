package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.unicom.thi360.identity.application.RedirectResolver;
import vn.unicom.thi360.identity.application.RedirectResolver.RedirectUser;
import vn.unicom.thi360.shared.security.Access;

class RedirectResolverTest {

    private static final String WEB_URL = "https://thi360.vn";
    private static final String ADMIN_URL = "https://admin.thi360.vn";

    private final RedirectResolver prodResolver = new RedirectResolver(WEB_URL, ADMIN_URL, List.of());
    private final RedirectResolver devResolver = new RedirectResolver(
            "http://localhost:3000",
            "http://localhost:3001",
            List.of());

    static Stream<Arguments> callbackValidationCases() {
        return Stream.of(
                Arguments.of("/admin/x", true, "/admin/x"),
                Arguments.of("/admin/x?tab=roles#top", true, "/admin/x?tab=roles#top"),
                Arguments.of("https://admin.thi360.vn/users", true, "https://admin.thi360.vn/users"),
                Arguments.of("https://thi360.vn/home?from=login", true, "https://thi360.vn/home?from=login"),
                Arguments.of("https://evil.example", false, "https://thi360.vn/home"),
                Arguments.of("https://admin.thi360.vn.evil.example/users", false, "https://thi360.vn/home"),
                Arguments.of("http://admin.thi360.vn/users", false, "https://thi360.vn/home"),
                Arguments.of("//evil.example", false, "https://thi360.vn/home"),
                Arguments.of("///evil.example", false, "https://thi360.vn/home"),
                Arguments.of("/\\evil", false, "https://thi360.vn/home"),
                Arguments.of("/%2f/evil.example", false, "https://thi360.vn/home"),
                Arguments.of("/%5cevil", false, "https://thi360.vn/home"),
                Arguments.of("javascript:alert(1)", false, "https://thi360.vn/home"),
                Arguments.of("data:text/html,<script>alert(1)</script>", false, "https://thi360.vn/home"),
                Arguments.of("https://thi360.vn@evil.example", false, "https://thi360.vn/home"),
                Arguments.of("https://user@admin.thi360.vn/users", false, "https://thi360.vn/home"),
                Arguments.of("https%253A%252F%252Fevil.example", false, "https://thi360.vn/home"),
                Arguments.of("https://admin.thi360.vn/%252F%252Fevil.example", false, "https://thi360.vn/home"),
                Arguments.of("/%252f%252fevil.example", false, "https://thi360.vn/home"),
                Arguments.of("/%25%32%66%25%32%66evil.example", false, "https://thi360.vn/home"),
                Arguments.of("https://admin.thi360.vn/%25%32%66%25%32%66evil.example", false, "https://thi360.vn/home"),
                Arguments.of("/admin/x?next=%25%32%66%25%32%66evil.example", false, "https://thi360.vn/home"),
                Arguments.of("/%25%35%63evil", false, "https://thi360.vn/home"),
                Arguments.of("https%3A%2F%2Fadmin.thi360.vn%2Fusers", false, "https://thi360.vn/home"),
                Arguments.of("", false, "https://thi360.vn/home"),
                Arguments.of("   ", false, "https://thi360.vn/home"),
                Arguments.of(" /admin/x", false, "https://thi360.vn/home"),
                Arguments.of("/admin/x\r\n", false, "https://thi360.vn/home")
        );
    }

    @ParameterizedTest
    @MethodSource("callbackValidationCases")
    void resolve_examUser_validatesCallbackUrl(String callbackUrl, boolean expectedValid, String expectedTarget) {
        String resolved = prodResolver.resolve(new RedirectUser(Access.EXAM, false), callbackUrl);
        assertThat(resolved).isEqualTo(expectedTarget);
        assertThat(prodResolver.validateCallbackUrl(callbackUrl).isPresent()).isEqualTo(expectedValid);
    }

    @Test
    void devAllowList_acceptsLocalhost3000And3001_andRejectsOtherPortsOrHosts() {
        assertThat(devResolver.resolve(new RedirectUser(Access.EXAM, false), "http://localhost:3001/users"))
                .isEqualTo("http://localhost:3001/users");
        assertThat(devResolver.resolve(new RedirectUser(Access.MANAGE, false), "http://localhost:3000/home"))
                .isEqualTo("http://localhost:3000/home");
        assertThat(devResolver.resolve(new RedirectUser(Access.MANAGE, false), "http://localhost:3999/users"))
                .isEqualTo("http://localhost:3001");
        assertThat(devResolver.resolve(new RedirectUser(Access.EXAM, false), "https://localhost:3000@evil.example"))
                .isEqualTo("http://localhost:3000/home");
    }

    @Test
    void roleDefaults_routeManageToAdminUrl_andExamToWebHome() {
        assertThat(prodResolver.resolve(new RedirectUser(Access.MANAGE, false), null))
                .isEqualTo(ADMIN_URL);
        assertThat(prodResolver.resolve(new RedirectUser(Access.EXAM, false), null))
                .isEqualTo(WEB_URL + "/home");
        assertThat(prodResolver.resolveHomeUrl(Access.MANAGE)).isEqualTo(ADMIN_URL);
        assertThat(prodResolver.resolveHomeUrl(Access.EXAM)).isEqualTo(WEB_URL + "/home");
    }

    @Test
    void mustChangePassword_takesPrecedence_andPreservesOnlyValidCallbackUrl() {
        assertThat(prodResolver.resolve(new RedirectUser(Access.MANAGE, true), null))
                .isEqualTo(WEB_URL + "/doi-mat-khau");

        assertThat(prodResolver.resolve(new RedirectUser(Access.MANAGE, true), "https://admin.thi360.vn/users"))
                .isEqualTo(WEB_URL + "/doi-mat-khau?callbackUrl=https%3A%2F%2Fadmin.thi360.vn%2Fusers");

        assertThat(prodResolver.resolve(new RedirectUser(Access.EXAM, true), "/home"))
                .isEqualTo(WEB_URL + "/doi-mat-khau?callbackUrl=%2Fhome");

        assertThat(prodResolver.resolve(new RedirectUser(Access.MANAGE, true), "https://evil.example"))
                .isEqualTo(WEB_URL + "/doi-mat-khau");

        assertThat(prodResolver.resolve(new RedirectUser(Access.EXAM, true), "/doi-mat-khau"))
                .isEqualTo(WEB_URL + "/doi-mat-khau");

        assertThat(prodResolver.resolve(new RedirectUser(Access.EXAM, true), "https://thi360.vn/doi-mat-khau"))
                .isEqualTo(WEB_URL + "/doi-mat-khau");

        assertThat(prodResolver.resolve(
                        new RedirectUser(Access.MANAGE, true),
                        "https://thi360.vn/doi-mat-khau?callbackUrl=https://admin.thi360.vn/users"))
                .isEqualTo(WEB_URL + "/doi-mat-khau?callbackUrl=https%3A%2F%2Fadmin.thi360.vn%2Fusers");

        assertThat(prodResolver.resolve(
                        new RedirectUser(Access.MANAGE, true),
                        "/doi-mat-khau?callbackUrl=https%3A%2F%2Fadmin.thi360.vn%2Fusers%3Ftab%3Droles"))
                .isEqualTo(WEB_URL + "/doi-mat-khau?callbackUrl=https%3A%2F%2Fadmin.thi360.vn%2Fusers%3Ftab%3Droles");

        assertThat(prodResolver.resolve(
                        new RedirectUser(Access.MANAGE, true),
                        "/doi-mat-khau?callbackUrl=https%3A%2F%2Fevil.example%2Fphish"))
                .isEqualTo(WEB_URL + "/doi-mat-khau");

        assertThat(prodResolver.resolve(
                        new RedirectUser(Access.EXAM, true),
                        "/doi-mat-khau?callbackUrl=%2Fdoi-mat-khau"))
                .isEqualTo(WEB_URL + "/doi-mat-khau");

        assertThat(prodResolver.resolve(
                        new RedirectUser(Access.EXAM, true),
                        "/doi-mat-khau?callbackUrl=%2Fdoi-mat-khau%3FcallbackUrl%3D%2Fhome"))
                .isEqualTo(WEB_URL + "/doi-mat-khau");
    }
}
