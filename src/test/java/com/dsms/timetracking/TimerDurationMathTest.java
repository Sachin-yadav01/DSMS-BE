package com.dsms.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimerDurationMathTest {
    @Test
    void pauseResumeExampleIsOneHour() {
        Instant start = Instant.parse("2026-10-01T20:00:00Z");
        Instant pause = Instant.parse("2026-10-01T20:30:00Z");
        Instant resume = Instant.parse("2026-10-01T20:45:00Z");
        Instant stop = Instant.parse("2026-10-01T21:15:00Z");
        long seconds = Duration.between(start, pause).toSeconds() + Duration.between(resume, stop).toSeconds();
        assertThat(seconds).isEqualTo(3600);
        assertThat(Duration.between(pause, resume).toSeconds()).isEqualTo(900);
    }
}
