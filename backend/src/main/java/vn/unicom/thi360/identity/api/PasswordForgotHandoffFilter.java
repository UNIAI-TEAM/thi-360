package vn.unicom.thi360.identity.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.unicom.thi360.identity.application.PasswordForgotDispatcher;

/**
 * Giao việc của {@code POST /auth/password/forgot} cho bộ thực thi sau khi phản hồi đã commit (TM 5.4): controller
 * chỉ ghi nhận việc; ở đây phản hồi được flush rồi mới {@link PasswordForgotDispatcher#handOff}. Nhờ vậy việc tra
 * tài khoản không thể bắt đầu trước khi caller nhận 202, kể cả khi bộ thực thi đang rảnh.
 */
@Component
public class PasswordForgotHandoffFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasswordForgotHandoffFilter.class);

    private final PasswordForgotDispatcher dispatcher;

    public PasswordForgotHandoffFilter(PasswordForgotDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (request.getAttribute(PasswordForgotDispatcher.PENDING_ATTRIBUTE) == null) {
            return;
        }
        try {
            response.flushBuffer();
        } catch (IOException | RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .addKeyValue("event", "password_reset.flush_failed")
                    .log("Password forgot response could not be flushed");
        }
        // Chỉ giao việc khi phản hồi đã commit: không bao giờ tra tài khoản trước khi caller có thể nhận 202. Flush lỗi
        // mà chưa commit thì bỏ việc (không trả 5xx: phản hồi vẫn là 202 như mọi yêu cầu forgot).
        if (response.isCommitted()) {
            dispatcher.handOff(request);
        } else {
            request.removeAttribute(PasswordForgotDispatcher.PENDING_ATTRIBUTE);
            log.atWarn()
                    .addKeyValue("event", "password_reset.handoff_skipped")
                    .log("Password forgot request dropped: response not committed");
        }
    }
}
