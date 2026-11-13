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
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger securityLog = LoggerFactory.getLogger("thi360.security");

    private RateLimitFilter activeRateLimitFilter;

    @PreDestroy
    public void cleanup() {
        if (activeRateLimitFilter != null) {
            activeRateLimitFilter.close();
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
                clientIpResolver, securityProperties, errorWriter, lettuceConnectionFactory);
        this.activeRateLimitFilter = rateLimitFilter;
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
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/ping",
                                "/api/v1/appearance",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**").permitAll()
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
                .build();
    }
}
