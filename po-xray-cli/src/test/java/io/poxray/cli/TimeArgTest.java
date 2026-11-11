package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** TimeArg.parse: local date-time in the clock's zone, ISO with offset, and -Nh / -Nm / -Nd relative to now. */
class TimeArgTest {

    /** A zone unlikely to be the JVM default, so a parser that ignores the clock's zone fails. */
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZONE);

    @Test
    void localDateTimeIsInTheClocksZone() {
        OffsetDateTime t = TimeArg.parse("2026-11-10T08:00", CLOCK);

        assertThat(t).isEqualTo(OffsetDateTime.parse("2026-11-10T08:00-05:00"));
    }

    @Test
    void isoWithOffsetIsKept() {
        assertThat(TimeArg.parse("2026-11-10T08:00:00+02:00", CLOCK))
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+02:00"));
        assertThat(TimeArg.parse("2026-11-10T07:30:15.250Z", CLOCK).toInstant())
                .isEqualTo(Instant.parse("2026-11-10T07:30:15.250Z"));
    }

    @Test
    void relativeHoursMinutesAndDaysCountBackFromNow() {
        assertThat(TimeArg.parse("-2h", CLOCK).toInstant()).isEqualTo(NOW.minus(Duration.ofHours(2)));
        assertThat(TimeArg.parse("-30m", CLOCK).toInstant()).isEqualTo(NOW.minus(Duration.ofMinutes(30)));
        assertThat(TimeArg.parse("-1d", CLOCK).toInstant()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        assertThat(TimeArg.parse("-24h", CLOCK).toInstant()).isEqualTo(NOW.minus(Duration.ofHours(24)));
    }

    @Test
    void unreadableTimeIsRejectedNamingTheValue() {
        assertThatThrownBy(() -> TimeArg.parse("yesterday", CLOCK))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("yesterday");
        assertThatThrownBy(() -> TimeArg.parse("-2x", CLOCK))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("-2x");
    }
}
