package vn.unicom.thi360.commerce.payment.gateway;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Trang giả lập {@code /mock-vnpay} chỉ tồn tại khi {@code thi360.vnpay.mode=MOCK} VÀ profile đang bật
 * là {@code dev}, {@code test} hoặc {@code e2e} (không có {@code prod}) (TM 8.4, plan B6, AC-18).
 */
public class MockVnpayPageCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        return MockVnpayModeCondition.isMockMode(env)
                && env.acceptsProfiles(Profiles.of("(dev | test | e2e) & !prod"));
    }
}
