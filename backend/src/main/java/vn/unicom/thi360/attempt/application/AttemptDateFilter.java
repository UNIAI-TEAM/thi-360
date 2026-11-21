package vn.unicom.thi360.attempt.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import vn.unicom.thi360.shared.api.BusinessRuleException;

/**
 * Shared date/instant query bound parser and LIKE pattern escaper matching {@link AttemptHistoryService}.
 */
final class AttemptDateFilter {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    record DateBound(Instant instant, boolean exclusive) {}

    private AttemptDateFilter() {}

    static DateBound parseBound(String raw, boolean startOfDay) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return new DateBound(OffsetDateTime.parse(trimmed).toInstant(), false);
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(trimmed);
                if (startOfDay) {
                    return new DateBound(date.atStartOfDay(DISPLAY_ZONE).toInstant(), false);
                }
                return new DateBound(date.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant(), true);
            } catch (DateTimeParseException ex) {
                throw new BusinessRuleException("INVALID_DATE_FILTER", null);
            }
        }
    }

    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
