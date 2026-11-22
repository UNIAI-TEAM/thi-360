package vn.unicom.thi360.shared.mail;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Mang {@code MDC} của luồng nộp việc sang luồng chạy việc (plan B15: chuyển việc sang luồng khác phải mang
 * MDC theo bằng {@link TaskDecorator}). {@code MDC} của luồng chạy được khôi phục sau việc, nên luồng dùng lại
 * không giữ khóa của việc trước.
 */
final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> submitted = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                apply(submitted);
                task.run();
            } finally {
                apply(previous);
            }
        };
    }

    private static void apply(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
