package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class MailRetryScheduleTests {

    @ParameterizedTest(name = "after failure #{0} wait {1} minutes")
    @CsvSource({"1,1", "2,5", "3,30", "4,120"})
    void delaysFollowOneFiveThirtyMinutesThenTwoHours(int failures, long minutes) {
        assertThat(MailRetrySchedule.delayAfter(failures)).isEqualTo(Duration.ofMinutes(minutes));
        assertThat(MailRetrySchedule.hasRetryLeft(failures)).isTrue();
    }

    @Test
    void fifthFailureEndsTheRetries() {
        assertThat(MailRetrySchedule.hasRetryLeft(5)).isFalse();
        assertThat(MailRetrySchedule.hasRetryLeft(6)).isFalse();
        assertThatThrownBy(() -> MailRetrySchedule.delayAfter(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noDelayBeforeTheFirstFailure() {
        assertThatThrownBy(() -> MailRetrySchedule.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
