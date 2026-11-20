package vn.unicom.thi360.commerce.promotion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.commerce.promotion.application.PromotionRedemptionService;
import vn.unicom.thi360.commerce.promotion.domain.DiscountType;
import vn.unicom.thi360.commerce.promotion.domain.Promotion;
import vn.unicom.thi360.commerce.promotion.domain.PromotionAudienceType;
import vn.unicom.thi360.commerce.promotion.domain.PromotionScope;
import vn.unicom.thi360.commerce.promotion.domain.PromotionStatus;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.shared.ids.Ids;

/** Base integration support for promotion repository and redemption tests with per-test cleanup. */
abstract class PromotionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PromotionRepository promotionRepository;

    @Autowired
    protected PromotionRedemptionService redemptionService;

    private final Set<UUID> createdPromotionIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void cleanupPromotions() {
        for (UUID promotionId : createdPromotionIds) {
            jdbc.update("DELETE FROM commerce.promotion_change_log WHERE promotion_id = ?", promotionId);
            jdbc.update("DELETE FROM commerce.promotion_redemption WHERE promotion_id = ?", promotionId);
            jdbc.update("DELETE FROM commerce.promotion WHERE id = ?", promotionId);
        }
        createdPromotionIds.clear();
    }

    protected UUID trackPromotion(UUID id) {
        createdPromotionIds.add(id);
        return id;
    }

    protected Promotion saveVoucher(Integer totalLimit, Integer perUserLimit) {
        Instant now = Instant.now();
        UUID id = trackPromotion(Ids.newId());
        String code = "VCH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Promotion voucher = Promotion.voucher(
                id,
                "Voucher " + code,
                "Test voucher",
                code,
                DiscountType.AMOUNT,
                20_000L,
                null,
                now.minus(1, ChronoUnit.HOURS),
                now.plus(1, ChronoUnit.DAYS),
                totalLimit,
                perUserLimit,
                false,
                PromotionScope.ALL,
                PromotionAudienceType.ALL,
                PromotionStatus.ACTIVE,
                now,
                null,
                null);
        return promotionRepository.saveAndFlush(voucher);
    }

    protected Map<String, Object> promotionRow(UUID promotionId) {
        return jdbc.queryForMap("SELECT * FROM commerce.promotion WHERE id = ?", promotionId);
    }

    protected int count(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    record Outcome<T>(T value, Throwable error) {
    }

    protected static <T> List<Outcome<T>> raceAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Outcome<>(task.call(), null);
                    } catch (Throwable t) {
                        return new Outcome<>(null, t);
                    }
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
