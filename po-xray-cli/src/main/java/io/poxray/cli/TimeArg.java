package io.poxray.cli;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A point in time given on the command line: a local date-time ({@code 2026-11-10T08:00}, in the clock's zone), an
 * ISO date-time with offset ({@code 2026-11-10T08:00:00+02:00}, {@code ...Z}), or a time back from now
 * ({@code -2h}, {@code -30m}, {@code -1d}).
 */
final class TimeArg {

    private static final Pattern RELATIVE = Pattern.compile("-(\\d{1,6})([hmd])");

    private TimeArg() {
    }

    /** The time {@code value} denotes at {@code clock}; an unreadable value is an IllegalArgumentException naming it. */
    static OffsetDateTime parse(String value, Clock clock) {
        String v = value == null ? "" : value.strip();
        Matcher m = RELATIVE.matcher(v);
        if (m.matches()) {
            long n = Long.parseLong(m.group(1));
            Duration back = switch (m.group(2)) {
                case "h" -> Duration.ofHours(n);
                case "m" -> Duration.ofMinutes(n);
                default -> Duration.ofDays(n);
            };
            return OffsetDateTime.ofInstant(clock.instant().minus(back), clock.getZone());
        }
        try {
            return OffsetDateTime.parse(v);
        } catch (DateTimeParseException withOffset) {
            try {
                return LocalDateTime.parse(v).atZone(clock.getZone()).toOffsetDateTime();
            } catch (DateTimeParseException local) {
                throw new IllegalArgumentException("Cannot read the time '" + value + "': use 2026-11-10T08:00 (local),"
                        + " an ISO date-time with offset, or -2h / -30m / -1d");
            }
        }
    }
}
