package vn.unicom.thi360.shared.mail;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Đăng ký bean {@code mailTaskScheduler} — bộ lập lịch riêng cho lịch gửi/dọn thư của {@link MailWorker}
 * qua {@code @Scheduled(scheduler = "mailTaskScheduler")} (plan B5).
 *
 * <p>Bean được thêm bằng {@link BeanDefinitionRegistryPostProcessor} — tức <em>sau</em> khi Spring Boot
 * kiểm tra {@code @ConditionalOnMissingBean} của {@code TaskSchedulingAutoConfiguration} — vì Boot bỏ
 * bộ lập lịch mặc định {@code taskScheduler} nếu container có sẵn bất kỳ bean {@code TaskScheduler} hay
 * {@code ScheduledExecutorService} nào. Nhờ thứ tự này, {@code taskScheduler} của Boot tồn tại và mọi
 * {@code @Scheduled} không chỉ định bộ lập lịch vẫn chạy trên đó; chỉ lịch thư chạy trên
 * {@code mailTaskScheduler} (2 luồng: một lượt gửi chậm không chặn việc dọn và ngược lại).
 */
@Component
class MailSchedulerRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    static final String SCHEDULER_BEAN = "mailTaskScheduler";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition(SCHEDULER_BEAN)
                || !environment.getProperty("thi360.mail.scheduling.enabled", Boolean.class, true)) {
            return;
        }
        BeanDefinition scheduler = BeanDefinitionBuilder.genericBeanDefinition(
                        ThreadPoolTaskScheduler.class,
                        () -> {
                            ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
                            s.setPoolSize(2);
                            s.setThreadNamePrefix("mail-worker-");
                            s.setDaemon(true);
                            s.initialize();
                            return s;
                        })
                .getBeanDefinition();
        registry.registerBeanDefinition(SCHEDULER_BEAN, scheduler);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Chỉ đăng ký định nghĩa bean; không cần sửa bean factory.
    }
}
