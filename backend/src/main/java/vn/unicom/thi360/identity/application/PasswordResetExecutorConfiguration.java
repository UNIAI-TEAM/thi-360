package vn.unicom.thi360.identity.application;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bộ thực thi riêng cho việc tra tài khoản và xếp thư của {@code forgot} (TM 5.4): chạy sau khi đã trả phản hồi,
 * để thời gian phản hồi không cho biết email có tài khoản hay không. Hàng đợi có giới hạn; đầy thì
 * {@link ThreadPoolExecutor.AbortPolicy} ném {@code RejectedExecutionException} cho {@link PasswordForgotDispatcher}
 * bỏ yêu cầu và vẫn trả 202. {@code defaultCandidate = false} để nơi tiêm {@code Executor} theo kiểu không bị nhập nhằng.
 */
@Configuration(proxyBeanMethods = false)
class PasswordResetExecutorConfiguration {

    static final String BEAN_NAME = "passwordResetExecutor";

    @Bean(value = BEAN_NAME, defaultCandidate = false)
    ThreadPoolTaskExecutor passwordResetExecutor(PasswordResetProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.executorPoolSize());
        executor.setMaxPoolSize(properties.executorPoolSize());
        executor.setQueueCapacity(properties.executorQueueCapacity());
        executor.setThreadNamePrefix("password-reset-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
