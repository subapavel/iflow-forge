package io.poxray.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The time forms of the message tools against a fixed clock in Europe/Berlin (UTC+01:00 in November). */
class MessageTimesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Europe/Berlin"));

    @Test
    void minutesBack() {
        OffsetDateTime t = MessageTimes.parse("-30m", CLOCK);

        assertThat(t.toInstant()).isEqualTo(Instant.parse("2026-11-10T11:30:00Z"));
        assertThat(t.getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    }

    @Test
    void daysBack() {
        assertThat(MessageTimes.parse("-1d", CLOCK).toInstant()).isEqualTo(Instant.parse("2026-11-09T12:00:00Z"));
    }

    @Test
    void hoursBack() {
        assertThat(MessageTimes.parse("-24h", CLOCK).toInstant()).isEqualTo(Instant.parse("2026-11-09T12:00:00Z"));
        assertThat(MessageTimes.parse("-2h", CLOCK).toInstant()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
    }

    @Test
    void localDateTimeIsInTheClocksZone() {
        assertThat(MessageTimes.parse("2026-11-10T08:00", CLOCK))
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+01:00"));
    }

    @Test
    void isoWithOffsetKeepsItsOffset() {
        OffsetDateTime t = MessageTimes.parse("2026-11-10T08:00:00-05:00", CLOCK);

        assertThat(t).isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00-05:00"));
        assertThat(t.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    void unreadableValueIsAnIllegalArgumentNamingIt() {
        assertThatThrownBy(() -> MessageTimes.parse("yesterday", CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yesterday");
    }
}
