package vn.unicom.thi360.commerce.bundle.devdata;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataBuilder.Report;

/**
 * Tự động khởi tạo bộ đề mẫu {@code DS-BO-SP2E-3MON} ngay sau {@code PublishedExamSetRunner} (Order 4).
 */
@Component
@Profile({"dev", "e2e"})
@Order(5)
@ConditionalOnProperty(name = "thi360.devdata.published-exams.enabled", havingValue = "true")
public class BundleDevDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BundleDevDataRunner.class);

    private final BundleDevDataBuilder builder;

    public BundleDevDataRunner(BundleDevDataBuilder builder) {
        this.builder = Objects.requireNonNull(builder);
    }

    @Override
    public void run(ApplicationArguments args) {
        Report report = builder.build();
        log.info("Bundle devdata ready event=\"bundle_devdata.ready\" created={} skipped={}",
                report.created(), report.skipped());
    }
}
