package org.zephyrsoft.trackworktime.pebble;

/** Immutable snapshot of tracking state sent to the watch. */
public final class PebbleStatus {

    /** Must match the task-name buffer declared on the watch (twt_status.h). */
    public static final int MAX_TASK_NAME_LEN = 32;

    private final boolean tracking;
    private final int taskId;
    private final String taskName;
    private final int workedBeforeMin;
    private final long segmentStartEpoch;

    private PebbleStatus(boolean tracking, int taskId, String taskName,
                         int workedBeforeMin, long segmentStartEpoch) {
        this.tracking = tracking;
        this.taskId = taskId;
        this.taskName = taskName;
        this.workedBeforeMin = workedBeforeMin;
        this.segmentStartEpoch = segmentStartEpoch;
    }

    public static PebbleStatus of(boolean tracking, int taskId, String taskName,
                                  int totalWorkedTodayMin, long segmentStartEpoch, long nowEpoch) {
        String name = taskName == null ? "" : taskName;
        if (name.length() > MAX_TASK_NAME_LEN) {
            name = name.substring(0, MAX_TASK_NAME_LEN);
        }
        if (tracking) {
            int runningMin = (int) Math.max(0, (nowEpoch - segmentStartEpoch) / 60);
            int workedBefore = Math.max(0, totalWorkedTodayMin - runningMin);
            return new PebbleStatus(true, taskId, name, workedBefore, segmentStartEpoch);
        } else {
            return new PebbleStatus(false, taskId, name, Math.max(0, totalWorkedTodayMin), 0L);
        }
    }

    public boolean isTracking() { return tracking; }
    public int taskId() { return taskId; }
    public String taskName() { return taskName; }
    public int workedBeforeMin() { return workedBeforeMin; }
    public long segmentStartEpoch() { return segmentStartEpoch; }
}
