package org.zephyrsoft.trackworktime.pebble;

/** Immutable snapshot of tracking state sent to the watch. */
public final class PebbleStatus {

    /** Must match the task-name buffer declared on the watch (twt_status.h). */
    public static final int MAX_TASK_NAME_LEN = 32;

    private final boolean tracking;
    private final int taskId;
    private final String taskName;
    private final int workedBeforeMin;
    private final int taskWorkedBeforeMin;
    private final long segmentStartEpoch;
    private final int dailyTargetMin;
    private final int taskTotalBeforeMin;
    private final int taskBudgetMin;
    private final int dayGrossBeforeMin;

    private PebbleStatus(boolean tracking, int taskId, String taskName,
                         int workedBeforeMin, int taskWorkedBeforeMin, long segmentStartEpoch,
                         int dailyTargetMin, int taskTotalBeforeMin, int taskBudgetMin,
                         int dayGrossBeforeMin) {
        this.tracking = tracking;
        this.taskId = taskId;
        this.taskName = taskName;
        this.workedBeforeMin = workedBeforeMin;
        this.taskWorkedBeforeMin = taskWorkedBeforeMin;
        this.segmentStartEpoch = segmentStartEpoch;
        this.dailyTargetMin = dailyTargetMin;
        this.taskTotalBeforeMin = taskTotalBeforeMin;
        this.taskBudgetMin = taskBudgetMin;
        this.dayGrossBeforeMin = dayGrossBeforeMin;
    }

    public static PebbleStatus of(boolean tracking, int taskId, String taskName,
                                  int totalWorkedTodayMin, int taskWorkedTodayMin,
                                  long segmentStartEpoch, long nowEpoch, int dailyTargetMin,
                                  int taskAllTimeMin, int taskBudgetMin, int dayGrossTodayMin) {
        String name = taskName == null ? "" : taskName;
        if (name.length() > MAX_TASK_NAME_LEN) {
            name = name.substring(0, MAX_TASK_NAME_LEN);
        }
        if (tracking) {
            int runningMin = (int) Math.max(0, (nowEpoch - segmentStartEpoch) / 60);
            // NOT clamped to >= 0: when an auto-pause (lunch) falls inside the single running
            // segment, the net day total is SHORTER than the running segment, so workedBefore is
            // legitimately negative. The watch reconstructs worked = workedBefore + running, so a
            // clamp to 0 would make it re-add the full segment and silently drop the auto-pause.
            // (The gross fields below stay clamped — the running segment is always part of their
            // gross totals, so they never legitimately go negative.)
            int workedBefore = totalWorkedTodayMin - runningMin;
            // taskWorked/taskTotal are passed in NET of today's auto-pause (the lunch was attributed
            // to this task upstream); like workedBefore they can legitimately go negative when the
            // running segment spans the pause, and the watch reconstructs task = before + running.
            int taskWorkedBefore = taskWorkedTodayMin - runningMin;
            int taskTotalBefore = taskAllTimeMin - runningMin;
            // dayGross stays GROSS (no auto-pause): it's the gross/gross denominator, and the running
            // segment is always part of it, so it never legitimately goes negative -> keep the clamp.
            int dayGrossBefore = Math.max(0, dayGrossTodayMin - runningMin);
            return new PebbleStatus(true, taskId, name, workedBefore, taskWorkedBefore,
                    segmentStartEpoch, dailyTargetMin, taskTotalBefore, Math.max(0, taskBudgetMin),
                    dayGrossBefore);
        } else {
            // only consumed while tracking (unbudgeted task percent) -> zeroed like the task fields
            return new PebbleStatus(false, taskId, name, Math.max(0, totalWorkedTodayMin), 0, 0L,
                    dailyTargetMin, 0, 0, 0);
        }
    }

    public boolean isTracking() { return tracking; }
    public int taskId() { return taskId; }
    public String taskName() { return taskName; }
    public int workedBeforeMin() { return workedBeforeMin; }
    public int taskWorkedBeforeMin() { return taskWorkedBeforeMin; }
    public long segmentStartEpoch() { return segmentStartEpoch; }
    public int dailyTargetMin() { return dailyTargetMin; }
    public int taskTotalBeforeMin() { return taskTotalBeforeMin; }
    public int taskBudgetMin() { return taskBudgetMin; }
    /** GROSS (no auto-pause) day total, excluding the running segment — the watch's
     *  denominator for the unbudgeted task percent (gross/gross, see PebbleTaskTimes). */
    public int dayGrossBeforeMin() { return dayGrossBeforeMin; }

    /**
     * Value equality over every field that is actually sent to the watch. This is what lets
     * {@link PebbleStatusPusher} skip a push whose payload would be byte-identical to the last
     * one — the per-minute watchdog would otherwise wake the watchface over Bluetooth 1440
     * times a day with unchanged data.
     * <p>
     * This is only meaningful because the snapshot is time-invariant by construction: the
     * "before" fields exclude the running segment (the watch reconstructs
     * {@code worked = before + running} from {@link #segmentStartEpoch()}), so while tracking
     * continues undisturbed the value does not drift minute to minute. {@code nowEpoch} is an
     * input to {@link #of} but deliberately not a field.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PebbleStatus)) {
            return false;
        }
        PebbleStatus other = (PebbleStatus) o;
        return tracking == other.tracking
                && taskId == other.taskId
                && workedBeforeMin == other.workedBeforeMin
                && taskWorkedBeforeMin == other.taskWorkedBeforeMin
                && segmentStartEpoch == other.segmentStartEpoch
                && dailyTargetMin == other.dailyTargetMin
                && taskTotalBeforeMin == other.taskTotalBeforeMin
                && taskBudgetMin == other.taskBudgetMin
                && dayGrossBeforeMin == other.dayGrossBeforeMin
                && taskName.equals(other.taskName);
    }

    @Override
    public int hashCode() {
        int result = tracking ? 1 : 0;
        result = 31 * result + taskId;
        result = 31 * result + taskName.hashCode();
        result = 31 * result + workedBeforeMin;
        result = 31 * result + taskWorkedBeforeMin;
        result = 31 * result + (int) (segmentStartEpoch ^ (segmentStartEpoch >>> 32));
        result = 31 * result + dailyTargetMin;
        result = 31 * result + taskTotalBeforeMin;
        result = 31 * result + taskBudgetMin;
        result = 31 * result + dayGrossBeforeMin;
        return result;
    }
}
