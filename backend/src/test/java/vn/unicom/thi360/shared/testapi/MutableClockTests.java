package vn.unicom.thi360.shared.testapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MutableClockTests {

    /** Đồng hồ nền do test điều khiển, để kiểm "giờ vẫn chạy sau khi tua" mà không phải chờ. */
    private static final class ManualBase extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-19T00:00:00Z"));

        void tick(Duration by) {
            now.updateAndGet(i -> i.plus(by));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    /**
     * Đồng hồ nền cho phép test dừng một lần đọc ngay sau khi nó đã lấy mẫu giờ nền, để dựng đúng
     * thứ tự xen kẽ giữa người đọc và người đặt mà không cần sleep.
     */
    private static final class GatedBase extends Clock {
        static final Instant START = Instant.parse("2026-11-19T00:00:00Z");

        private volatile Instant now = START;
        private final AtomicBoolean armed = new AtomicBoolean();
        private volatile boolean staged;
        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);
        private final CountDownLatch sampled = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void set(Instant value) {
            now = value;
        }

        /** Lần gọi {@code instant()} kế tiếp (ở thread nào cũng được) lấy mẫu rồi dừng tới khi {@link #release()}. */
        void armNextSample() {
            armed.set(true);
        }

        void awaitSampled() throws InterruptedException {
            assertThat(sampled.await(5, TimeUnit.SECONDS)).isTrue();
        }

        /**
         * Như {@link #armNextSample()}, nhưng lần gọi kế tiếp còn dừng <em>trước</em> khi lấy mẫu (tới
         * {@link #proceed()}), để test chen các lần ghi vào giữa lúc người đọc đã đọc trạng thái và
         * lúc nó lấy mẫu giờ nền.
         */
        void armStagedSample() {
            staged = true;
            armed.set(true);
        }

        void awaitArrived() throws InterruptedException {
            assertThat(arrived.await(5, TimeUnit.SECONDS)).isTrue();
        }

        void proceed() {
            proceed.countDown();
        }

        void release() {
            release.countDown();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            boolean gated = armed.compareAndSet(true, false);
            if (gated && staged) {
                arrived.countDown();
                await(proceed);
            }
            Instant value = now;
            if (gated) {
                sampled.countDown();
                await(release);
            }
            return value;
        }

        private static void await(CountDownLatch latch) {
            try {
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @Test
    void startsAtBaseTime() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        assertThat(clock.instant()).isEqualTo(base.instant());
        assertThat(clock.offset()).isEqualTo(Duration.ZERO);
    }

    @Test
    void timeKeepsRunningAfterAdvance() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        Instant afterAdvance = clock.advance(Duration.ofHours(3));
        assertThat(afterAdvance).isEqualTo(base.instant().plus(Duration.ofHours(3)));

        base.tick(Duration.ofSeconds(5));
        assertThat(clock.instant()).isEqualTo(afterAdvance.plusSeconds(5));
    }

    @Test
    void timeKeepsRunningAfterSetAt() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        Instant target = Instant.parse("2027-01-01T08:00:00Z");

        assertThat(clock.setAt(target)).isEqualTo(target);

        base.tick(Duration.ofMinutes(2));
        assertThat(clock.instant()).isEqualTo(target.plus(Duration.ofMinutes(2)));
    }

    @Test
    void advancesAccumulate() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        clock.advance(Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(45));

        assertThat(clock.instant()).isEqualTo(base.instant().plusSeconds(75));
    }

    @Test
    void resetReturnsToBaseTime() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        clock.advance(Duration.ofDays(2));

        assertThat(clock.reset()).isEqualTo(base.instant());

        base.tick(Duration.ofSeconds(7));
        assertThat(clock.instant()).isEqualTo(base.instant());
        assertThat(clock.offset()).isEqualTo(Duration.ZERO);
    }

    @Test
    void negativeAdvanceIsRejectedAndKeepsOffset() {
        MutableClock clock = new MutableClock(new ManualBase());
        clock.advance(Duration.ofMinutes(1));

        assertThatThrownBy(() -> clock.advance(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(clock.offset()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void withZoneViewFollowsTheMutableClock() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        ZoneId hanoi = ZoneId.of("Asia/Ho_Chi_Minh");
        Clock view = clock.withZone(hanoi);

        clock.advance(Duration.ofHours(1));

        assertThat(view.getZone()).isEqualTo(hanoi);
        assertThat(view.instant()).isEqualTo(clock.instant());
        assertThat(clock.withZone(ZoneOffset.UTC)).isSameAs(clock);
    }

    // R1-04: giờ kết quả được kiểm trước khi ghi; quá khoảng thì giữ nguyên trạng thái cũ.
    @Test
    void outOfRangeAdvanceAndSetAreRejectedAndKeepTheClockReadable() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);
        clock.advance(Duration.ofMinutes(1));
        Duration before = clock.offset();

        List<Runnable> rejected = List.of(
                () -> clock.advance(Duration.ofSeconds(Long.MAX_VALUE)),
                () -> clock.advance(Duration.ofDays(365L * 8000)),
                () -> clock.setAt(Instant.MAX),
                () -> clock.setAt(Instant.MIN),
                () -> clock.setAt(MutableClock.SUPPORTED_MAX.plusSeconds(1)),
                () -> clock.setAt(MutableClock.SUPPORTED_MIN.minusSeconds(1)));
        for (Runnable call : rejected) {
            assertThatThrownBy(call::run).isInstanceOf(MutableClock.OutOfRangeException.class);
            assertThat(clock.offset()).isEqualTo(before);
            assertThat(clock.instant()).isEqualTo(base.instant().plus(before));
        }
    }

    @Test
    void boundariesOfTheSupportedRangeAreAccepted() {
        ManualBase base = new ManualBase();
        MutableClock clock = new MutableClock(base);

        assertThat(clock.setAt(MutableClock.SUPPORTED_MAX)).isEqualTo(MutableClock.SUPPORTED_MAX);
        assertThat(clock.setAt(MutableClock.SUPPORTED_MIN)).isEqualTo(MutableClock.SUPPORTED_MIN);
        assertThat(clock.reset()).isEqualTo(base.instant());
    }

    // R1-08: người đọc đã lấy mẫu giờ nền thì không được trộn với độ lệch của lần đặt xen vào.
    @Test
    void readRacingASetReturnsTheClockBeforeOrAfterNeverAMixture() throws Exception {
        GatedBase base = new GatedBase();
        MutableClock clock = new MutableClock(base);
        Instant target = GatedBase.START.plusSeconds(100);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            base.armNextSample();
            Future<Instant> read = pool.submit(clock::instant);
            base.awaitSampled();

            // Người đặt chạy khi người đọc đang dừng sau khi lấy mẫu giờ nền (tại START).
            base.set(GatedBase.START.plusSeconds(10));
            assertThat(clock.setAt(target)).isEqualTo(target);
            base.release();

            Instant read1 = read.get(5, TimeUnit.SECONDS);
            // Trước lần đặt là START; sau lần đặt là target (+ giờ nền đã chạy thêm). Không phải 90 giây lai.
            assertThat(read1).isIn(GatedBase.START, target);
            assertThat(read1).isNotEqualTo(GatedBase.START.plusSeconds(90));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void advanceRacingAnotherAdvanceIsNotLostAndAcknowledgesItsOwnTransition() throws Exception {
        GatedBase base = new GatedBase();
        MutableClock clock = new MutableClock(base);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            base.armNextSample();
            Future<Instant> slow = pool.submit(() -> clock.advance(Duration.ofSeconds(10)));
            base.awaitSampled();

            // Lần tua thứ hai chạy xong khi lần thứ nhất mới lấy mẫu, chưa ghi.
            Instant second = clock.advance(Duration.ofSeconds(5));
            assertThat(second).isEqualTo(GatedBase.START.plusSeconds(5));
            base.release();

            Instant first = slow.get(5, TimeUnit.SECONDS);
            assertThat(clock.offset()).isEqualTo(Duration.ofSeconds(15));
            assertThat(first).isEqualTo(GatedBase.START.plusSeconds(15));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void setRacingAnAdvanceAcknowledgesTheValueItWrote() throws Exception {
        GatedBase base = new GatedBase();
        MutableClock clock = new MutableClock(base);
        Instant target = Instant.parse("2031-05-06T07:08:09Z");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            base.armNextSample();
            Future<Instant> set = pool.submit(() -> clock.setAt(target));
            base.awaitSampled();
            clock.advance(Duration.ofSeconds(5));
            base.release();

            assertThat(set.get(5, TimeUnit.SECONDS)).isEqualTo(target);
            assertThat(clock.instant()).isEqualTo(target);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentAdvancesAccumulateExactly() throws Exception {
        MutableClock clock = new MutableClock(new ManualBase());
        int threads = 8;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        clock.advance(Duration.ofSeconds(1));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertThat(clock.offset()).isEqualTo(Duration.ofSeconds((long) threads * perThread));
        } finally {
            pool.shutdownNow();
        }
    }

    // R2-02: đặt rồi đặt lại về cùng độ lệch (0 -> khác 0 -> 0) quanh một lần đọc không được để người
    // đọc nhận mẫu giờ nền của trạng thái giữa cộng với độ lệch của trạng thái đầu.
    @Test
    void setThenResetAcrossAReadNeverYieldsAMixture() throws Exception {
        GatedBase base = new GatedBase();
        MutableClock clock = new MutableClock(base);
        Instant afterSet = GatedBase.START.plusSeconds(100);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            base.armStagedSample();
            Future<Instant> read = pool.submit(clock::instant);
            base.awaitArrived(); // người đọc đã đọc trạng thái đầu (độ lệch 0), chưa lấy mẫu giờ nền

            assertThat(clock.setAt(afterSet)).isEqualTo(afterSet); // trạng thái giữa
            base.set(GatedBase.START.plusSeconds(10));
            base.proceed();
            base.awaitSampled(); // mẫu giờ nền được lấy khi trạng thái giữa đang hiệu lực

            base.set(GatedBase.START.plusSeconds(20));
            Instant afterReset = clock.reset(); // về độ lệch 0 như trạng thái đầu
            assertThat(afterReset).isEqualTo(GatedBase.START.plusSeconds(20));
            base.release();

            Instant read1 = read.get(5, TimeUnit.SECONDS);
            // Giờ đồng hồ thực sự có: START+110 (trạng thái giữa tại mẫu) hoặc START+20 (sau khi về 0).
            // START+10 là mẫu của trạng thái giữa cộng độ lệch 0 của trạng thái đầu: chưa từng có.
            assertThat(read1).isIn(GatedBase.START.plusSeconds(110), GatedBase.START.plusSeconds(20));
            assertThat(read1).isNotEqualTo(GatedBase.START.plusSeconds(10));
        } finally {
            pool.shutdownNow();
        }
    }
    // R3-02: kiểm khoảng thất bại trên độ lệch đã bị đổi trong lúc kiểm phải thử lại với trạng thái
    // mới, không từ chối. Dàn cảnh: đồng hồ ở MAX-2s; lần tua +1s đọc trạng thái rồi dừng trước khi
    // lấy mẫu; giờ nền chạy +2s, một reset xen vào đưa độ lệch về 0; khi nhả đọc, kết quả trên mẫu
    // trễ là MAX+1s (quá khoảng) nhưng trên trạng thái mới là hợp lệ: phải ghi được.
    @Test
    void advanceRejectedByAStaleValidationRetriesAgainstTheNewState() throws Exception {
        GatedBase base = new GatedBase();
        MutableClock clock = new MutableClock(base);
        clock.setAt(MutableClock.SUPPORTED_MAX.minusSeconds(2));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            base.armStagedSample();
            Future<Instant> advance = pool.submit(() -> clock.advance(Duration.ofSeconds(1)));
            base.awaitArrived(); // lần tua đã đọc trạng thái (MAX-2s), chưa lấy mẫu giờ nền

            base.set(GatedBase.START.plusSeconds(2));
            assertThat(clock.reset()).isEqualTo(GatedBase.START.plusSeconds(2)); // trạng thái mới, độ lệch 0
            base.proceed();
            base.awaitSampled(); // mẫu trễ được lấy xong, lần tua vào bước kiểm khoảng
            base.release();

            // Mẫu trễ (START+2s) cộng độ lệch cũ cho MAX+1s: quá khoảng; trạng thái mới (độ lệch 0)
            // cho START+3s: hợp lệ. Kết quả ghi được phải là của trạng thái mới.
            Instant moved = advance.get(5, TimeUnit.SECONDS);
            assertThat(moved).isEqualTo(GatedBase.START.plusSeconds(3));
            assertThat(clock.offset()).isEqualTo(Duration.ofSeconds(1));
            assertThat(clock.instant()).isEqualTo(GatedBase.START.plusSeconds(3));
        } finally {
            pool.shutdownNow();
        }
    }
}
