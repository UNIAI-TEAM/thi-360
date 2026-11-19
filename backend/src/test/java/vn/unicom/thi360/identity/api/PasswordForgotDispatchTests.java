package vn.unicom.thi360.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import vn.unicom.thi360.identity.application.AuthService;
import vn.unicom.thi360.identity.application.PasswordForgotDispatcher;
import vn.unicom.thi360.identity.application.PasswordResetService;
import vn.unicom.thi360.shared.security.ClientIpResolver;
import vn.unicom.thi360.shared.web.AppMessages;

/**
 * AC-2, AC-3 (plan ID-2): {@code forgot} trả phản hồi trước khi tra tài khoản và không phân biệt email; hàng đợi
 * đầy vẫn 202 cùng thân. Việc chỉ được giao cho bộ thực thi sau khi phản hồi đã commit: test chạy controller trong
 * {@link PasswordForgotHandoffFilter} thật với request/response servlet và ghi lại thời điểm giao so với commit,
 * không đo thời gian.
 */
class PasswordForgotDispatchTests {

    private PasswordResetService passwordResetService;
    private ClientIpResolver clientIpResolver;
    private final List<Runnable> queued = new ArrayList<>();
    private final List<Boolean> committedWhenHandedOff = new ArrayList<>();
    private MockHttpServletResponse currentResponse;
    private Executor capturingExecutor;
    private final AppMessages messages = AppMessages.defaultInstance();

    @BeforeEach
    void setUp() {
        passwordResetService = mock(PasswordResetService.class);
        clientIpResolver = mock(ClientIpResolver.class);
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn("203.0.113.7");
        queued.clear();
        committedWhenHandedOff.clear();
        capturingExecutor = task -> {
            committedWhenHandedOff.add(currentResponse.isCommitted());
            queued.add(task);
        };
    }

    private record Outcome(ResponseEntity<PasswordForgotResponse> response, int queuedBeforeResponseCompleted) {
    }

    /** Chạy một yêu cầu forgot qua bộ lọc giao việc thật; ghi lại số việc đã giao khi controller vừa trả về. */
    private Outcome forgot(Executor executor, String email) throws Exception {
        PasswordForgotDispatcher dispatcher = new PasswordForgotDispatcher(executor, passwordResetService);
        AuthController controller = new AuthController(
                mock(AuthService.class), dispatcher, passwordResetService, clientIpResolver, messages);
        MockHttpServletRequest request = new MockHttpServletRequest();
        currentResponse = new MockHttpServletResponse();
        AtomicReference<ResponseEntity<PasswordForgotResponse>> result = new AtomicReference<>();
        AtomicInteger queuedInChain = new AtomicInteger(-1);
        int queuedBefore = queued.size();
        new PasswordForgotHandoffFilter(dispatcher).doFilter(request, currentResponse, (req, res) -> {
            result.set(controller.forgot(new PasswordForgotRequest(email), (HttpServletRequest) req));
            res.getWriter().write("accepted");
            queuedInChain.set(queued.size() - queuedBefore);
        });
        return new Outcome(result.get(), queuedInChain.get());
    }

    @Test
    void respondsBeforeLookup() throws Exception {
        Outcome existing = forgot(capturingExecutor, "has.account@thi360.vn");
        Outcome unknown = forgot(capturingExecutor, "nobody@thi360.vn");

        // Khi controller trả về chưa có việc nào được giao; việc chỉ được giao sau khi phản hồi đã commit.
        assertThat(existing.queuedBeforeResponseCompleted()).isZero();
        assertThat(unknown.queuedBeforeResponseCompleted()).isZero();
        assertThat(committedWhenHandedOff).containsExactly(true, true);
        assertThat(existing.response().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.response().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.response().getBody()).isEqualTo(existing.response().getBody());
        assertThat(existing.response().getBody().message()).isNotBlank();
        verifyNoInteractions(passwordResetService);

        // Cả hai đi cùng một đường mã: đúng một lần giao việc mỗi yêu cầu, rồi cùng gọi issue.
        queued.forEach(Runnable::run);
        InOrder order = inOrder(passwordResetService);
        order.verify(passwordResetService).issue("has.account@thi360.vn", "203.0.113.7");
        order.verify(passwordResetService).issue("nobody@thi360.vn", "203.0.113.7");
    }

    @Test
    void fullQueueStillAccepted() throws Exception {
        Executor full = task -> {
            throw new RejectedExecutionException("queue is full");
        };

        Outcome saturated = forgot(full, "has.account@thi360.vn");

        assertThat(saturated.response().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(saturated.response().getBody())
                .isEqualTo(forgot(capturingExecutor, "other@thi360.vn").response().getBody());
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

            Outcome outcome = forgot(real, "has.account@thi360.vn");

            assertThat(outcome.response().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        } finally {
            release.countDown();
            real.shutdown();
        }
    }

    @Test
    void backgroundFailureIsContainedInTheTask() throws Exception {
        doThrow(new IllegalStateException("mail queue down")).when(passwordResetService)
                .issue(anyString(), anyString());

        assertThat(forgot(capturingExecutor, "has.account@thi360.vn").response().getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        queued.forEach(Runnable::run);

        verify(passwordResetService).issue("has.account@thi360.vn", "203.0.113.7");
    }

    @Test
    void flushFailureBeforeCommitDoesNotHandOffAndDoesNotFailTheCaller() throws Exception {
        PasswordForgotDispatcher dispatcher = new PasswordForgotDispatcher(capturingExecutor, passwordResetService);
        AuthController controller = new AuthController(
                mock(AuthService.class), dispatcher, passwordResetService, clientIpResolver, messages);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse failing = new MockHttpServletResponse() {
            @Override
            public void flushBuffer() {
                throw new java.io.UncheckedIOException(new java.io.IOException("flush failed before the response was committed"));
            }
        };
        currentResponse = failing;
        AtomicReference<ResponseEntity<PasswordForgotResponse>> result = new AtomicReference<>();

        new PasswordForgotHandoffFilter(dispatcher).doFilter(request, failing, (req, res) -> {
            result.set(controller.forgot(new PasswordForgotRequest("has.account@thi360.vn"), (HttpServletRequest) req));
            res.getWriter().write("accepted");
        });

        assertThat(failing.isCommitted()).isFalse();
        assertThat(queued).as("no lookup is handed off for an uncommitted response").isEmpty();
        assertThat(request.getAttribute(PasswordForgotDispatcher.PENDING_ATTRIBUTE)).isNull();
        assertThat(result.get().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void flushFailureAfterCommitStillHandsOff() throws Exception {
        PasswordForgotDispatcher dispatcher = new PasswordForgotDispatcher(capturingExecutor, passwordResetService);
        AuthController controller = new AuthController(
                mock(AuthService.class), dispatcher, passwordResetService, clientIpResolver, messages);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse committedThenFails = new MockHttpServletResponse() {
            @Override
            public void flushBuffer() {
                super.flushBuffer();
                throw new java.io.UncheckedIOException(new java.io.IOException("client went away after the commit"));
            }
        };
        currentResponse = committedThenFails;

        new PasswordForgotHandoffFilter(dispatcher).doFilter(request, committedThenFails, (req, res) -> {
            controller.forgot(new PasswordForgotRequest("has.account@thi360.vn"), (HttpServletRequest) req);
            res.getWriter().write("accepted");
        });

        assertThat(committedWhenHandedOff).containsExactly(true);
        assertThat(queued).hasSize(1);
    }

    private static void awaitQuietly(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
