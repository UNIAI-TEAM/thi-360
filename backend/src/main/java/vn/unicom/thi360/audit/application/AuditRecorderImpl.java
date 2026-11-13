package vn.unicom.thi360.audit.application;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.unicom.thi360.audit.domain.SensitiveDataMasker;
import vn.unicom.thi360.audit.infrastructure.AuditOutboxPayload;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Service
public class AuditRecorderImpl implements AuditRecorder {

    public static final String AUDIT_OUTBOX_TOPIC = "audit";

    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public AuditRecorderImpl(OutboxPublisher outboxPublisher, Clock clock) {
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    @Override
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        UUID eventId = Ids.newId();
        Instant occurredAt = Instant.now(clock);
        ResolvedActor actor = resolveActor(event);
        HttpServletRequest request = currentRequest();
        String traceId = resolveTraceId(request);
        String ip = resolveClientIp(request);

        Map<String, Object> maskedBefore = SensitiveDataMasker.mask(event.before());
        Map<String, Object> maskedAfter = SensitiveDataMasker.mask(event.after());

        AuditOutboxPayload payload = new AuditOutboxPayload(
                eventId,
                occurredAt,
                actor.actorId(),
                actor.actorUsername(),
                actor.actorRoles(),
                actor.actorType(),
                event.module(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.targetLabel(),
                maskedBefore,
                maskedAfter,
                event.reason(),
                event.outcome() != null ? event.outcome() : AuditOutcome.SUCCESS,
                event.errorCode(),
                traceId,
                ip);

        outboxPublisher.publish(eventId, AUDIT_OUTBOX_TOPIC, payload);
    }

    private ResolvedActor resolveActor(AuditEvent event) {
        if (event.actorType() != null || event.actorId() != null || event.actorUsername() != null) {
            AuditActorType type = event.actorType();
            if (type == null) {
                type = (event.actorId() != null || event.actorUsername() != null)
                        ? AuditActorType.USER
                        : AuditActorType.SYSTEM;
            }
            List<String> roles = event.actorRoles() != null ? event.actorRoles() : List.of();
            return new ResolvedActor(event.actorId(), event.actorUsername(), roles, type);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            List<String> roles = auth.getAuthorities() == null
                    ? List.of()
                    : auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(Objects::nonNull)
                            .toList();

            UUID actorId = null;
            String actorUsername = auth.getName();
            boolean isPlatform = roles.contains("ROLE_PLATFORM_ADMIN") || roles.contains("PLATFORM_ADMIN");

            if (auth.getPrincipal() instanceof Jwt jwt) {
                actorId = parseUuidOrNull(jwt.getSubject());
                String preferred = jwt.getClaimAsString("preferred_username");
                if (preferred != null && !preferred.isBlank()) {
                    actorUsername = preferred;
                }
                if (!isPlatform && jwt.getIssuer() != null && jwt.getIssuer().toString().endsWith("/realms/master")) {
                    isPlatform = true;
                }
            } else {
                actorId = parseUuidOrNull(auth.getName());
            }

            AuditActorType actorType = isPlatform ? AuditActorType.PLATFORM : AuditActorType.USER;
            return new ResolvedActor(actorId, actorUsername, roles, actorType);
        }

        return new ResolvedActor(null, null, List.of(), AuditActorType.SYSTEM);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String mdcTrace = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        if (mdcTrace != null && !mdcTrace.isBlank()) {
            return mdcTrace;
        }
        if (request != null) {
            Object attr = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
            if (attr instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        return null;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIdx = forwarded.indexOf(',');
            String first = (commaIdx >= 0 ? forwarded.substring(0, commaIdx) : forwarded).trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : null;
    }

    private static UUID parseUuidOrNull(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(candidate.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record ResolvedActor(
            UUID actorId,
            String actorUsername,
            List<String> actorRoles,
            AuditActorType actorType
    ) {
    }
}
