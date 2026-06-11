package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class PebbleStatusTest {

    @Test
    public void tracking_excludesRunningSegmentFromAllTotals() {
        // total today=130, task today=80, task all-time=300, running=30min, target=450, budget=1200,
        // gross day=160
        PebbleStatus s = PebbleStatus.of(
                true, 7, "Customer A", 130, 80, 1_000_000L, 1_000_000L + 30 * 60, 450, 300, 1200, 160);
        assertThat(s.isTracking()).isTrue();
        assertThat(s.taskId()).isEqualTo(7);
        assertThat(s.taskName()).isEqualTo("Customer A");
        assertThat(s.workedBeforeMin()).isEqualTo(100);
        assertThat(s.taskWorkedBeforeMin()).isEqualTo(50);
        assertThat(s.taskTotalBeforeMin()).isEqualTo(270);   // 300 - 30
        assertThat(s.taskBudgetMin()).isEqualTo(1200);
        assertThat(s.segmentStartEpoch()).isEqualTo(1_000_000L);
        assertThat(s.dailyTargetMin()).isEqualTo(450);
    }

    @Test
    public void workedBeforeMayBeNegative_soAutoPauseSurvivesTheWatchReconstruction() {
        // Continuously clocked in since before lunch: the auto-pause (e.g. 30min) is deducted from
        // the day total, but it sits IN THE MIDDLE of the single running segment. So the running
        // segment (360min) is LONGER than the net worked total (330min). workedBefore must be allowed
        // to go negative (-30), because the watch reconstructs worked = workedBefore + running, and
        // clamping it to 0 would make the watch re-add the full segment and silently drop the lunch.
        int totalNet = 330;             // 6h elapsed minus a 30min auto-pause
        int runningMin = 360;           // segmentStart was 360min ago (since before lunch)
        PebbleStatus s = PebbleStatus.of(
                true, 7, "Customer A", totalNet, 360, 1_000_000L, 1_000_000L + runningMin * 60,
                450, 0, 0, 360);
        assertThat(s.workedBeforeMin()).isEqualTo(-30);
        // watch reconstruction: workedBefore + running == net worked total (lunch preserved)
        assertThat(s.workedBeforeMin() + runningMin).isEqualTo(totalNet);
    }

    @Test
    public void notTracking_taskFieldsZeroed() {
        PebbleStatus s = PebbleStatus.of(false, 0, "", 90, 0, 0L, 1_000_000L, 450, 300, 1200, 95);
        assertThat(s.isTracking()).isFalse();
        assertThat(s.workedBeforeMin()).isEqualTo(90);
        assertThat(s.taskWorkedBeforeMin()).isEqualTo(0);
        assertThat(s.taskTotalBeforeMin()).isEqualTo(0);
        assertThat(s.taskBudgetMin()).isEqualTo(0);
        assertThat(s.segmentStartEpoch()).isEqualTo(0L);
        assertThat(s.dailyTargetMin()).isEqualTo(450);
    }

    @Test
    public void dailyTargetIsStoredVerbatim_notReducedByRunningSegment() {
        PebbleStatus s = PebbleStatus.of(true, 1, "X", 130, 80, 1_000_000L, 1_000_000L + 30 * 60, 0, 0, 0, 160);
        assertThat(s.dailyTargetMin()).isEqualTo(0);
    }

    @Test
    public void budgetIsStoredVerbatim_notReducedByRunningSegment() {
        PebbleStatus s = PebbleStatus.of(true, 1, "X", 130, 80, 1_000_000L, 1_000_000L + 30 * 60, 450, 300, 1200, 160);
        assertThat(s.taskBudgetMin()).isEqualTo(1200);
    }

    @Test
    public void taskTotalBeforeNeverNegative() {
        // running (30) larger than task all-time (10) -> clamp to 0
        PebbleStatus s = PebbleStatus.of(true, 1, "X", 130, 10, 1_000_000L, 1_000_000L + 30 * 60, 450, 10, 1200, 160);
        assertThat(s.taskTotalBeforeMin()).isEqualTo(0);
    }

    @Test
    public void tracking_dayGrossExcludesRunningSegment() {
        // gross day today=198 incl. the 30min running segment -> before=168
        PebbleStatus s = PebbleStatus.of(
                true, 7, "Customer A", 168, 198, 1_000_000L, 1_000_000L + 30 * 60, 450, 0, 0, 198);
        assertThat(s.dayGrossBeforeMin()).isEqualTo(168);
    }

    @Test
    public void dayGrossBeforeNeverNegative() {
        // running (30) larger than the gross day sum (10) -> clamp to 0
        PebbleStatus s = PebbleStatus.of(
                true, 1, "X", 130, 10, 1_000_000L, 1_000_000L + 30 * 60, 450, 0, 0, 10);
        assertThat(s.dayGrossBeforeMin()).isEqualTo(0);
    }

    @Test
    public void notTracking_dayGrossZeroed() {
        // only consumed while tracking (unbudgeted task percent) -> zeroed like the task fields
        PebbleStatus s = PebbleStatus.of(false, 0, "", 90, 0, 0L, 1_000_000L, 450, 300, 1200, 95);
        assertThat(s.dayGrossBeforeMin()).isEqualTo(0);
    }

    @Test
    public void taskNameIsTruncatedToFitWatchBuffer() {
        String longName = "This is a very long task name beyond the buffer size";
        PebbleStatus s = PebbleStatus.of(true, 1, longName, 0, 0, 5L, 5L, 450, 0, 0, 0);
        assertThat(s.taskName().length()).isEqualTo(PebbleStatus.MAX_TASK_NAME_LEN);
    }

    @Test
    public void nullTaskNameBecomesEmptyString() {
        PebbleStatus s = PebbleStatus.of(true, 1, null, 0, 0, 5L, 5L, 450, 0, 0, 0);
        assertThat(s.taskName()).isEqualTo("");
    }
}
