package vn.unicom.thi360.shared.testapi;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Chốt chặn khởi động (plan A6): profile {@code e2e} bật cùng {@code prod} thì ứng dụng không khởi
 * động. Chạy ở {@link BeanFactoryPostProcessor} nên thất bại trước khi bất kỳ bean nào (Flyway, kết
 * nối cơ sở dữ liệu) được tạo.
 */
final class E2eProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment.acceptsProfiles(Profiles.of(TestApiPaths.PROD_PROFILE))) {
            throw new IllegalStateException(
                    "Profile '" + TestApiPaths.E2E_PROFILE + "' must not be active together with profile '"
                            + TestApiPaths.PROD_PROFILE + "': it opens /api/v1/test/** and a controllable clock");
        }
    }
}
