package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PebbleStatusTest {

    @Test
    public void tracking_excludesRunningSegmentFromBothTotals() {
        // total today=130, current task today=80, running segment=30min, daily target=450
        PebbleStatus s = PebbleStatus.of(
                true, 7, "Customer A", 130, 80, 1_000_000L, 1_000_000L + 30 * 60, 450);
        assertThat(s.isTracking()).isTrue();
        assertThat(s.taskId()).isEqualTo(7);
        assertThat(s.taskName()).isEqualTo("Customer A");
        assertThat(s.workedBeforeMin()).isEqualTo(100);
        assertThat(s.taskWorkedBeforeMin()).isEqualTo(50);
        assertThat(s.segmentStartEpoch()).isEqualTo(1_000_000L);
        assertThat(s.dailyTargetMin()).isEqualTo(450);
    }

    @Test
    public void notTracking_workedBeforeIsTotal_taskZero_segmentStartZero() {
        PebbleStatus s = PebbleStatus.of(false, 0, "", 90, 0, 0L, 1_000_000L, 450);
        assertThat(s.isTracking()).isFalse();
        assertThat(s.workedBeforeMin()).isEqualTo(90);
        assertThat(s.taskWorkedBeforeMin()).isEqualTo(0);
        assertThat(s.segmentStartEpoch()).isEqualTo(0L);
        assertThat(s.dailyTargetMin()).isEqualTo(450);
    }

    @Test
    public void dailyTargetIsStoredVerbatim_notReducedByRunningSegment() {
        // running segment (30) must NOT be subtracted from the target
        PebbleStatus s = PebbleStatus.of(true, 1, "X", 130, 80, 1_000_000L, 1_000_000L + 30 * 60, 0);
        assertThat(s.dailyTargetMin()).isEqualTo(0);
    }

    @Test
    public void taskWorkedBeforeNeverNegative() {
        // running (30) larger than task total (10) -> clamp to 0
        PebbleStatus s = PebbleStatus.of(true, 1, "X", 130, 10, 1_000_000L, 1_000_000L + 30 * 60, 450);
        assertThat(s.taskWorkedBeforeMin()).isEqualTo(0);
    }

    @Test
    public void taskNameIsTruncatedToFitWatchBuffer() {
        String longName = "This is a very long task name beyond the buffer size";
        PebbleStatus s = PebbleStatus.of(true, 1, longName, 0, 0, 5L, 5L, 450);
        assertThat(s.taskName().length()).isEqualTo(PebbleStatus.MAX_TASK_NAME_LEN);
    }

    @Test
    public void nullTaskNameBecomesEmptyString() {
        PebbleStatus s = PebbleStatus.of(true, 1, null, 0, 0, 5L, 5L, 450);
        assertThat(s.taskName()).isEqualTo("");
    }
}
