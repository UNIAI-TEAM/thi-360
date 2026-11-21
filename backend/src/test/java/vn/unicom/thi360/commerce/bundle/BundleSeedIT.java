package vn.unicom.thi360.commerce.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataBuilder;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataBuilder.Report;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataRunner;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleCodeNormalizer;
import vn.unicom.thi360.commerce.bundle.domain.BundleItem;
import vn.unicom.thi360.commerce.bundle.domain.BundleResultView;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.bundle.domain.BundleVersion;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleItemRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleRepository;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleVersionRepository;
import vn.unicom.thi360.commerce.order.domain.ProductType;
import vn.unicom.thi360.commerce.storefront.domain.SaleState;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository;
import vn.unicom.thi360.commerce.storefront.infrastructure.StorefrontProductRepository.StoredProduct;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Slice 4 ITs cho devdata bộ đề 3 môn SP2E {@code DS-BO-SP2E-3MON} (AC-28).
 */
class BundleSeedIT extends AbstractIntegrationTest {

    private static final String BUNDLE_CODE = "DS-BO-SP2E-3MON";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired private ApplicationContext context;
    @Autowired private BundleDevDataBuilder bundleDevDataBuilder;
    @Autowired private BundleRepository bundleRepository;
    @Autowired private BundleItemRepository bundleItemRepository;
    @Autowired private BundleVersionRepository bundleVersionRepository;
    @Autowired private StorefrontProductRepository storefrontProductRepository;
    @Autowired private PublishedExamPort publishedExamPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @BeforeEach
    void seedAll() {
        PublishedExamSetSupport.ensureBuilt(context);
        bundleDevDataBuilder.build();
    }

    @Test
    void seedsSp2eBundleIdempotentlyAndProjectsStorefront() {
        Bundle bundle = bundleRepository.findByCodeNormalized(BundleCodeNormalizer.normalize(BUNDLE_CODE))
                .orElseThrow();
        assertThat(bundle.getCode()).isEqualTo(BUNDLE_CODE);
        assertThat(bundle.getStatus()).isEqualTo(BundleStatus.PUBLISHED);
        assertThat(bundle.getServingVersion()).isEqualTo(1);
        assertThat(bundle.getListPriceVnd()).isEqualTo(99_000L);
        assertThat(bundle.getAttemptsPerExam()).isEqualTo(3);
        assertThat(bundle.getResultView()).isEqualTo(BundleResultView.SCORE_AND_ANSWERS);

        List<BundleItem> items = bundleItemRepository.findByBundleIdOrderByPositionAsc(bundle.getId());
        assertThat(items).hasSize(3);
        List<String> memberCodes = items.stream()
                .map(item -> publishedExamPort.findServing(item.getExamId()).orElseThrow().version().code())
                .toList();
        assertThat(memberCodes).containsExactly("DS-SP2E-MATH-2026", "DS-SP2E-VAN-OLD", "DS-SP2E-ANH-2026");

        BundleVersion version1 = bundleVersionRepository.findByBundleIdAndVersionNo(bundle.getId(), 1).orElseThrow();
        assertThat(version1.getSnapshot().items()).hasSize(3);
        assertThat(countBundleVersions(bundle.getId())).isEqualTo(1L);

        StoredProduct product = storefrontProductRepository.findByBundleId(bundle.getId()).orElseThrow();
        assertThat(product.productType()).isEqualTo(ProductType.BUNDLE);
        assertThat(product.saleState()).isEqualTo(SaleState.ON_SALE);
        assertThat(product.code()).isEqualTo(BUNDLE_CODE);
        assertThat(product.listPriceVnd()).isEqualTo(99_000L);
        assertThat(product.attemptsGranted()).isEqualTo(3);

        // Chạy lại builder lần 2: idempotent hoàn toàn, không nhân đôi dữ liệu hay tăng phiên bản.
        long bundleCountBefore = countBundles();
        long itemCountBefore = countBundleItems(bundle.getId());
        long versionCountBefore = countBundleVersions(bundle.getId());
        Report replay = bundleDevDataBuilder.build();
        assertThat(replay.created()).isZero();
        assertThat(replay.skipped()).isEqualTo(1);
        assertThat(countBundles()).isEqualTo(bundleCountBefore);
        assertThat(countBundleItems(bundle.getId())).isEqualTo(itemCountBefore);
        assertThat(countBundleVersions(bundle.getId())).isEqualTo(versionCountBefore);
    }

    @Test
    void runnerAnnotationsRunAfterExamSetOnDevAndE2e() throws Exception {
        Profile profile = BundleDevDataRunner.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder("dev", "e2e");

        Order bundleOrder = BundleDevDataRunner.class.getAnnotation(Order.class);
        Class<?> examRunnerClass = Class.forName("vn.unicom.thi360.exam.devdata.PublishedExamSetRunner");
        Order examOrder = examRunnerClass.getAnnotation(Order.class);
        assertThat(bundleOrder).isNotNull();
        assertThat(examOrder).isNotNull();
        assertThat(bundleOrder.value()).isGreaterThan(examOrder.value());

        ConditionalOnProperty prop = BundleDevDataRunner.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(prop).isNotNull();
        assertThat(prop.name()).containsExactly("thi360.devdata.published-exams.enabled");
        assertThat(prop.havingValue()).isEqualTo("true");
    }

    private long countBundles() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM commerce.bundle WHERE code_normalized = ?",
                Long.class,
                BundleCodeNormalizer.normalize(BUNDLE_CODE));
    }

    private long countBundleItems(java.util.UUID bundleId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM commerce.bundle_item WHERE bundle_id = ?",
                Long.class,
                bundleId);
    }

    private long countBundleVersions(java.util.UUID bundleId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM commerce.bundle_version WHERE bundle_id = ?",
                Long.class,
                bundleId);
    }
}
