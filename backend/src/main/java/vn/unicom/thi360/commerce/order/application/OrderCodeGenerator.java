package vn.unicom.thi360.commerce.order.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.commerce.order.infrastructure.PurchaseOrderRepository;

/**
 * Sinh mã đơn hàng ({@code DH + yyMMdd + 6 ký tự [A-Z0-9]}) và mã giao dịch VNPAY ({@code <mã đơn><2 chữ số>})
 * theo TM E14.
 */
@Component
public class OrderCodeGenerator {

    private static final ZoneId GMT_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_PREFIX_FMT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final char[] ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int SUFFIX_LENGTH = 6;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Clock clock;

    public OrderCodeGenerator(PurchaseOrderRepository purchaseOrderRepository, Clock clock) {
        this.purchaseOrderRepository = Objects.requireNonNull(purchaseOrderRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public String nextOrderCode() {
        String datePart = DATE_PREFIX_FMT.format(clock.instant().atZone(GMT_PLUS_7));
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = "DH" + datePart + randomSuffix();
            if (!purchaseOrderRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique order code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    public String txnRef(String orderCode, int attemptSeq) {
        Objects.requireNonNull(orderCode, "orderCode must not be null");
        if (attemptSeq < 1) {
            throw new IllegalArgumentException("attemptSeq must be >= 1");
        }
        return orderCode + String.format("%02d", attemptSeq);
    }

    private String randomSuffix() {
        char[] buf = new char[SUFFIX_LENGTH];
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            buf[i] = ALPHANUM[random.nextInt(ALPHANUM.length)];
        }
        return new String(buf);
    }
}
