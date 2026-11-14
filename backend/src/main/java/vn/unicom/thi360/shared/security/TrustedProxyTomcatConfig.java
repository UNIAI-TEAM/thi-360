package vn.unicom.thi360.shared.security;

import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class TrustedProxyTomcatConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> trustedProxyTomcatCustomizer(
            ClientIpResolver clientIpResolver) {
        String regex = clientIpResolver.trustedProxyMatcher().toTomcatRegex();
        return factory -> {
            RemoteIpValve existing = null;
            for (Valve valve : factory.getEngineValves()) {
                if (valve instanceof RemoteIpValve riv) {
                    existing = riv;
                    break;
                }
            }
            if (existing == null) {
                existing = new RemoteIpValve();
                factory.addEngineValves(existing);
            }
            existing.setRemoteIpHeader(ClientIpResolver.X_FORWARDED_FOR);
            existing.setProtocolHeader("X-Forwarded-Proto");
            existing.setInternalProxies(regex);
            existing.setTrustedProxies(null);
        };
    }
}
