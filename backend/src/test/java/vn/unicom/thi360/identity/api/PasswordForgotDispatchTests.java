package vn.unicom.thi360.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.identity.application.PasswordForgotDispatcher;
import vn.unicom.thi360.identity.application.PasswordResetService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * AC-2, AC-3 (plan ID-2): {@code forgot} trả phản hồi trước khi tra tài khoản và không phân biệt email; hàng đợi
 * đầy vẫn 202 cùng thân. Test cấu trúc: bộ thực thi và service là mock, nên không phụ thuộc đo thời gian.
 */
class PasswordForgotDispatchTests {

    private PasswordResetService passwordResetService;
    private ClientIpResolver clientIpResolver;
    private final List<Runnable> queued = new ArrayList<>();
    private Executor capturingExecutor;
    private final AppMessages messages = AppMessages.defaultInstance();

    @BeforeEach
    void setUp() {
        passwordResetService = mock(PasswordResetService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("203.0.113.7");
        queued.clear();
        capturingExecutor = queued::add;
    }

    private AuthController controller(Executor executor) {
        return new AuthController(
                mock(AuthService.class),
                new PasswordForgotDispatcher(executor, passwordResetService),
                passwordResetService,
                clientIpResolver,
                messages);
    }

    private ResponseEntity<PasswordForgotResponse> forgot(AuthController controller, String email) {
        return controller.forgot(new PasswordForgotRequest(email), new MockHttpServletRequest());
    }

    @Test
    void respondsBeforeLookup() {
        AuthController controller = controller(capturingExecutor);

        ResponseEntity<PasswordForgotResponse> existing = forgot(controller, "has.account@thi360.vn");
        ResponseEntity<PasswordForgotResponse> unknown = forgot(controller, "nobody@thi360.vn");

        // Phản hồi đã trả mà chưa có lần tra tài khoản nào: việc nằm trong hàng đợi của bộ thực thi.
        verifyNoInteractions(passwordResetService);
        assertThat(queued).hasSize(2);
        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getBody()).isEqualTo(existing.getBody());
        assertThat(existing.getBody().message()).isNotBlank();

        // Cả hai đi cùng một đường mã: đúng một lần giao việc mỗi yêu cầu, rồi cùng gọi issue.
        queued.forEach(Runnable::run);
        InOrder order = inOrder(passwordResetService);
        order.verify(passwordResetService).issue("has.account@thi360.vn", "203.0.113.7");
        order.verify(passwordResetService).issue("nobody@thi360.vn", "203.0.113.7");
    }

    @Test
    void fullQueueStillAccepted() {
        Executor full = task -> {
            throw new RejectedExecutionException("queue is full");
        };
        AuthController controller = controller(full);

        ResponseEntity<PasswordForgotResponse> response = forgot(controller, "has.account@thi360.vn");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(
                forgot(controller(capturingExecutor), "other@thi360.vn").getBody());
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void realBoundedExecutorRejectsWhenSaturatedWithoutFailingTheCaller() throws Exception {
        ThreadPoolTaskExecutor real = new ThreadPoolTaskExecutor();
        real.setCorePoolSize(1);
        real.setMaxPoolSize(1);
        real.setQueueCapacity(1);
        real.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        real.initialize();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try {
            real.execute(() -> awaitQuietly(release));
            real.execute(() -> awaitQuietly(release));

            AuthController controller = controller(real);
            ResponseEntity<PasswordForgotResponse> response = forgot(controller, "has.account@thi360.vn");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        } finally {
            release.countDown();
            real.shutdown();
        }
    }

    @Test
    void backgroundFailureIsContainedInTheTask() {
        doThrow(new IllegalStateException("mail queue down")).when(passwordResetService)
                .issue(anyString(), anyString());
        AuthController controller = controller(capturingExecutor);

        assertThat(forgot(controller, "has.account@thi360.vn").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        queued.forEach(Runnable::run);

        verify(passwordResetService).issue("has.account@thi360.vn", "203.0.113.7");
    }

    private static void awaitQuietly(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
