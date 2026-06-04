package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PebbleStatusTest {

    @Test
    public void tracking_excludesRunningSegmentFromWorkedBefore() {
        PebbleStatus s = PebbleStatus.of(
                true, 7, "Customer A", 130, 1_000_000L, 1_000_000L + 30 * 60);
        assertThat(s.isTracking()).isTrue();
        assertThat(s.taskId()).isEqualTo(7);
        assertThat(s.taskName()).isEqualTo("Customer A");
        assertThat(s.workedBeforeMin()).isEqualTo(100);
        assertThat(s.segmentStartEpoch()).isEqualTo(1_000_000L);
    }

    @Test
    public void notTracking_workedBeforeIsTotal_andSegmentStartZero() {
        PebbleStatus s = PebbleStatus.of(false, 0, "", 90, 0L, 1_000_000L);
        assertThat(s.isTracking()).isFalse();
        assertThat(s.workedBeforeMin()).isEqualTo(90);
        assertThat(s.segmentStartEpoch()).isEqualTo(0L);
    }

    @Test
    public void taskNameIsTruncatedToFitWatchBuffer() {
        String longName = "This is a very long task name beyond the buffer size";
        PebbleStatus s = PebbleStatus.of(true, 1, longName, 0, 5L, 5L);
        assertThat(s.taskName().length()).isAtMost(PebbleStatus.MAX_TASK_NAME_LEN);
    }
}
