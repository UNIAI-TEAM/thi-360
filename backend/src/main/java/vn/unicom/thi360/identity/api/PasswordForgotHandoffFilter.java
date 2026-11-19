package vn.unicom.thi360.identity.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        if (request.getAttribute(PasswordForgotDispatcher.PENDING_ATTRIBUTE) != null) {
            try {
                response.flushBuffer();
            } finally {
                dispatcher.handOff(request);
            }
        }
    }
}
