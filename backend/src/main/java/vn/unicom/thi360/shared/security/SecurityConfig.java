package vn.unicom.thi360.shared.security;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.unicom.thi360.shared.testapi.TestApiPaths;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");

    private RateLimitFilter activeRateLimitFilter;
    private RateLimitFilter activeUploadRateLimitFilter;

    @PreDestroy
    public void cleanup() {
        if (activeRateLimitFilter != null) {
            activeRateLimitFilter.close();
        }
        if (activeUploadRateLimitFilter != null) {
            activeUploadRateLimitFilter.close();
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TwoIssuerJwtDecoder twoIssuerJwtDecoder,
            SessionStore sessionStore,
            ClientIpResolver clientIpResolver,
            SecurityProperties securityProperties,
            SecurityErrorWriter errorWriter,
            LettuceConnectionFactory lettuceConnectionFactory,
            @Autowired(required = false) SessionAuthzResolver authzResolver)
            throws Exception {

        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                clientIpResolver, securityProperties, errorWriter, lettuceConnectionFactory, true);
        RateLimitFilter uploadRateLimitFilter = new RateLimitFilter(
                clientIpResolver, securityProperties, errorWriter, lettuceConnectionFactory, false);
        this.activeRateLimitFilter = rateLimitFilter;
        this.activeUploadRateLimitFilter = uploadRateLimitFilter;
        CookieAuthFilter cookieAuthFilter = new CookieAuthFilter();
        SessionFilter sessionFilter = new SessionFilter(
                sessionStore, twoIssuerJwtDecoder, errorWriter, authzResolver);
        CsrfFilter csrfFilter = new CsrfFilter(errorWriter);
        AdminOriginFilter adminOriginFilter = new AdminOriginFilter(securityProperties, errorWriter);

        AuthenticationEntryPoint entryPoint = (request, response, authException) -> {
            securityLog.atWarn()
                    .addKeyValue("event", "auth.unauthenticated")
                    .addKeyValue("path", request.getRequestURI())
                    .log("Unauthenticated request rejected");
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "UNAUTHENTICATED");
        };

        AccessDeniedHandler deniedHandler = (request, response, accessDeniedException) -> {
            securityLog.atWarn()
                    .addKeyValue("event", "auth.forbidden")
                    .addKeyValue("path", request.getRequestURI())
                    .log("Access denied");
            errorWriter.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "FORBIDDEN");
        };

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RateLimitFilter.RegistrationRoute::matches).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/mobile/login",
                                "/api/v1/auth/mobile/refresh",
                                "/api/v1/auth/mobile/logout",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/ping",
                                "/api/v1/appearance",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                // CM-1 (TM 10.2): the public storefront; /api/v1/public/** is limited per IP by RateLimitFilter.
                                "/api/v1/public/**",
                                // CM-3a (TM 8.4): mock VNPAY page (active only in dev/test/e2e when MODE=MOCK).
                                "/mock-vnpay",
                                "/mock-vnpay/**").permitAll()
                        // /api/v1/test/**: chỉ profile e2e có handler (shared/testapi), nên chuỗi này chỉ thấy
                        // tiền tố ở profile e2e (ẩn danh gọi được). Profile khác do chuỗi riêng
                        // TestApiAbsentSecurityConfig trả 404 trước khi xét xác thực.
                        .requestMatchers(TestApiPaths.PATTERN).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").access((authentication, context) -> {
                            var authObj = authentication.get();
                            boolean allowed = authObj != null
                                    && authObj.isAuthenticated()
                                    && authObj.getPrincipal() instanceof CurrentUser user
                                    && user.access() == Access.MANAGE;
                            return new AuthorizationDecision(allowed);
                        })
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(twoIssuerJwtDecoder))
                        .authenticationEntryPoint(entryPoint))
                .addFilterBefore(cookieAuthFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, CookieAuthFilter.class)
                .addFilterAfter(sessionFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, SessionFilter.class)
                .addFilterAfter(adminOriginFilter, CsrfFilter.class)
                .addFilterAfter(uploadRateLimitFilter, AuthorizationFilter.class)
                .build();
    }
}
