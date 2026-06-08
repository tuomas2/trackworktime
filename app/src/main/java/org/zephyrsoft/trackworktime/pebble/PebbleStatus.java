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

    private PebbleStatus(boolean tracking, int taskId, String taskName,
                         int workedBeforeMin, int taskWorkedBeforeMin, long segmentStartEpoch,
                         int dailyTargetMin) {
        this.tracking = tracking;
        this.taskId = taskId;
        this.taskName = taskName;
        this.workedBeforeMin = workedBeforeMin;
        this.taskWorkedBeforeMin = taskWorkedBeforeMin;
        this.segmentStartEpoch = segmentStartEpoch;
        this.dailyTargetMin = dailyTargetMin;
    }

    public static PebbleStatus of(boolean tracking, int taskId, String taskName,
                                  int totalWorkedTodayMin, int taskWorkedTodayMin,
                                  long segmentStartEpoch, long nowEpoch, int dailyTargetMin) {
        String name = taskName == null ? "" : taskName;
        if (name.length() > MAX_TASK_NAME_LEN) {
            name = name.substring(0, MAX_TASK_NAME_LEN);
        }
        if (tracking) {
            int runningMin = (int) Math.max(0, (nowEpoch - segmentStartEpoch) / 60);
            int workedBefore = Math.max(0, totalWorkedTodayMin - runningMin);
            int taskWorkedBefore = Math.max(0, taskWorkedTodayMin - runningMin);
            return new PebbleStatus(true, taskId, name, workedBefore, taskWorkedBefore,
                    segmentStartEpoch, dailyTargetMin);
        } else {
            return new PebbleStatus(false, taskId, name, Math.max(0, totalWorkedTodayMin), 0, 0L,
                    dailyTargetMin);
        }
    }

    public boolean isTracking() { return tracking; }
    public int taskId() { return taskId; }
    public String taskName() { return taskName; }
    public int workedBeforeMin() { return workedBeforeMin; }
    public int taskWorkedBeforeMin() { return taskWorkedBeforeMin; }
    public long segmentStartEpoch() { return segmentStartEpoch; }
    public int dailyTargetMin() { return dailyTargetMin; }
}
