package vn.unicom.thi360.exam.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.unicom.thi360.exam.domain.solver.SolverLimits;

@Configuration(proxyBeanMethods = false)
class ExamSolverConfiguration {
    @Bean
    SolverLimits solverLimits(ExamSolverProperties properties) {
        return properties.toLimits();
    }
}
