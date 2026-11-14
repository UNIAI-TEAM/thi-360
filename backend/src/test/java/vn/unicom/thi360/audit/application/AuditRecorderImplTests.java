package vn.unicom.thi360.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.unicom.thi360.audit.infrastructure.AuditOutboxPayload;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.shared.outbox.OutboxPublisher;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

class AuditRecorderImplTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveActor_withAppCurrentUserToken_setsUuidUsernameRolesAndUserType() {
        OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);
        AuditRecorderImpl recorder = new AuditRecorderImpl(outboxPublisher, FIXED_CLOCK);

        UUID actorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CurrentUser principal = new CurrentUser(
                actorId,
                "student.user",
                Realm.APP,
                Access.EXAM,
                PermissionSet.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new CurrentUserAuthenticationToken(principal, "sid-app"));

        recorder.record(AuditEvent.success(
                "identity", "PASSWORD_CHANGED", "USER", actorId.toString(), "student.user"));

        ArgumentCaptor<AuditOutboxPayload> captor = ArgumentCaptor.forClass(AuditOutboxPayload.class);
        verify(outboxPublisher).publish(any(UUID.class), eq(AuditRecorderImpl.AUDIT_OUTBOX_TOPIC), captor.capture());

        AuditOutboxPayload payload = captor.getValue();
        assertThat(payload.actorId()).isEqualTo(actorId);
        assertThat(payload.actorUsername()).isEqualTo("student.user");
        assertThat(payload.actorType()).isEqualTo(AuditActorType.USER);
        assertThat(payload.actorRoles()).containsExactlyInAnyOrder("ACCESS_EXAM", "REALM_APP");
    }

    @Test
    void resolveActor_withMasterCurrentUserToken_setsUuidUsernameRolesAndPlatformType() {
        OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);
        AuditRecorderImpl recorder = new AuditRecorderImpl(outboxPublisher, FIXED_CLOCK);

        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CurrentUser principal = new CurrentUser(
                actorId,
                "platform",
                Realm.MASTER,
                Access.MANAGE,
                PermissionSet.ofCodes(List.of("AUDIT.VIEW")));
        SecurityContextHolder.getContext().setAuthentication(
                new CurrentUserAuthenticationToken(principal, "sid-master"));

        recorder.record(AuditEvent.success("audit", "AUDIT_VIEWED", "AUDIT", "all", "all"));

        ArgumentCaptor<AuditOutboxPayload> captor = ArgumentCaptor.forClass(AuditOutboxPayload.class);
        verify(outboxPublisher).publish(any(UUID.class), eq(AuditRecorderImpl.AUDIT_OUTBOX_TOPIC), captor.capture());

        AuditOutboxPayload payload = captor.getValue();
        assertThat(payload.actorId()).isEqualTo(actorId);
        assertThat(payload.actorUsername()).isEqualTo("platform");
        assertThat(payload.actorType()).isEqualTo(AuditActorType.PLATFORM);
        assertThat(payload.actorRoles())
                .containsExactlyInAnyOrder("ACCESS_MANAGE", "REALM_MASTER", "PERM_AUDIT.VIEW");
    }

    @Test
    void resolveActor_withExplicitActorOverride_overridesSecurityContextPrincipal() {
        OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);
        AuditRecorderImpl recorder = new AuditRecorderImpl(outboxPublisher, FIXED_CLOCK);

        UUID contextId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        CurrentUser principal = new CurrentUser(
                contextId,
                "student.user",
                Realm.APP,
                Access.EXAM,
                PermissionSet.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new CurrentUserAuthenticationToken(principal, "sid-app"));

        UUID explicitId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        recorder.record(AuditEvent.builder("identity", "LOGIN")
                .actor(explicitId, "platform", List.of("PLATFORM_ADMIN"), AuditActorType.PLATFORM)
                .target("PLATFORM_ADMIN", explicitId.toString(), "platform")
                .build());

        ArgumentCaptor<AuditOutboxPayload> captor = ArgumentCaptor.forClass(AuditOutboxPayload.class);
        verify(outboxPublisher).publish(any(UUID.class), eq(AuditRecorderImpl.AUDIT_OUTBOX_TOPIC), captor.capture());

        AuditOutboxPayload payload = captor.getValue();
        assertThat(payload.actorId()).isEqualTo(explicitId);
        assertThat(payload.actorUsername()).isEqualTo("platform");
        assertThat(payload.actorRoles()).containsExactly("PLATFORM_ADMIN");
        assertThat(payload.actorType()).isEqualTo(AuditActorType.PLATFORM);
    }

    @Test
    void resolveActor_withoutAuthentication_fallsBackToSystem() {
        OutboxPublisher outboxPublisher = mock(OutboxPublisher.class);
        AuditRecorderImpl recorder = new AuditRecorderImpl(outboxPublisher, FIXED_CLOCK);

        recorder.record(AuditEvent.success(
                "audit", "AUDIT_ARCHIVED", "AUDIT_PARTITION", "audit_event_2023q1", "audit_event_2023q1"));

        ArgumentCaptor<AuditOutboxPayload> captor = ArgumentCaptor.forClass(AuditOutboxPayload.class);
        verify(outboxPublisher).publish(any(UUID.class), eq(AuditRecorderImpl.AUDIT_OUTBOX_TOPIC), captor.capture());

        AuditOutboxPayload payload = captor.getValue();
        assertThat(payload.actorId()).isNull();
        assertThat(payload.actorUsername()).isNull();
        assertThat(payload.actorRoles()).isEmpty();
        assertThat(payload.actorType()).isEqualTo(AuditActorType.SYSTEM);
    }
}
