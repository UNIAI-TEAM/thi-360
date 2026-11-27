package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.application.AccountSelfDeletionService;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient.CreateUserCommand;
import vn.unicom.thi360.identity.spi.AccountDeletionState;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

class AccountDeletionStateIT extends AbstractKeycloakIntegrationTest {

    private static final String PASSWORD = "Valid@1234";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AccountDeletionState accountDeletionState;
    @Autowired private AccountSelfDeletionService accountSelfDeletionService;
    @Autowired private KeycloakIdentityClient keycloakClient;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID userId : createdUserIds) {
            jdbc.update("DELETE FROM shared.email_message WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redis.delete(List.of("user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
    }

    @Test
    void mandatoryTransactionAndForShareBlocksSelfDeletionUntilFulfillmentTxEnds() throws Exception {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        String username = "delstate." + sfx;
        String email = username + "@thi360-test.example";
        UUID userId = keycloakClient.createUser(new CreateUserCommand(
                username, email, "State", "Lock", PASSWORD, true, false, true));
        Long seq = jdbc.queryForObject("SELECT nextval('identity.user_code_seq')", Long.class);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO identity.user_account
                    (id, code, username, username_normalized, full_name, email, email_normalized,
                     email_verified_at, preferred_locale, source, status, must_change_password,
                     created_at, created_by, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, 'State Lock', ?, ?, ?, 'vi', 1, 2, false, ?, ?, ?, ?, 0)
                """, userId, String.format("U%06d", seq), username, username.toLowerCase(),
                email, email.toLowerCase(), now, now, userId, now, userId);
        createdUserIds.add(userId);

        assertThatThrownBy(() -> accountDeletionState.lockAndIsDeleted(userId))
                .isInstanceOf(IllegalTransactionStateException.class);

        CountDownLatch shareHeld = new CountDownLatch(1);
        CountDownLatch releaseShare = new CountDownLatch(1);
        CountDownLatch deleteFinished = new CountDownLatch(1);
        AtomicBoolean sawDeletedInShareTx = new AtomicBoolean(true);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CurrentUser currentUser = new CurrentUser(userId, username, Realm.APP, Access.EXAM, PermissionSet.empty());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> fulfillmentFuture = pool.submit(() -> {
                tx.executeWithoutResult(status -> {
                    boolean deleted = accountDeletionState.lockAndIsDeleted(userId);
                    sawDeletedInShareTx.set(deleted);
                    shareHeld.countDown();
                    try {
                        assertThat(releaseShare.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                });
            });

            assertThat(shareHeld.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(sawDeletedInShareTx.get()).isFalse();

            Future<?> deleteFuture = pool.submit(() -> {
                try {
                    accountSelfDeletionService.delete(currentUser, PASSWORD);
                } finally {
                    deleteFinished.countDown();
                }
            });

            assertThat(deleteFinished.await(600, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT deleted_at FROM identity.user_account WHERE id = ?", Object.class, userId))
                    .isNull();

            releaseShare.countDown();
            fulfillmentFuture.get(10, TimeUnit.SECONDS);
            deleteFuture.get(30, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject(
                "SELECT deleted_at FROM identity.user_account WHERE id = ?", Object.class, userId))
                .isNotNull();
    }
}
