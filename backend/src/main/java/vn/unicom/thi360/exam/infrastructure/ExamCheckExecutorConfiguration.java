package vn.unicom.thi360.exam.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class ExamCheckExecutorConfiguration {
    @Bean(value = "examCheckExecutor", defaultCandidate = false)
    ThreadPoolTaskExecutor examCheckExecutor(ExamCheckExecutionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setThreadNamePrefix("exam-check-");
        return executor;
    }
}
