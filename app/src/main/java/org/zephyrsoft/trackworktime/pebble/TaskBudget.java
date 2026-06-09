package org.zephyrsoft.trackworktime.pebble;

import org.zephyrsoft.trackworktime.util.DateTimeUtil;

/**
 * Shared per-task budget arithmetic + display text. A budget of {@code null} or {@code <= 0}
 * means "no budget".
 */
public final class TaskBudget {

    private TaskBudget() {}

    /** Returns true when the task has an effective budget set. */
    public static boolean hasBudget(Integer budgetMinutes) {
        return budgetMinutes != null && budgetMinutes > 0;
    }

    /**
     * Rounded percent {@code 100 * workedMinutes / budgetMinutes}; may exceed 100 (overtime).
     * Returns {@code -1} when there is no budget (callers hide the percent). Negative worked
     * minutes are clamped to 0.
     */
    public static int percent(int workedMinutes, Integer budgetMinutes) {
        if (!hasBudget(budgetMinutes)) {
            return -1;
        }
        int worked = Math.max(0, workedMinutes);
        long base = budgetMinutes;
        return (int) ((worked * 100L + base / 2) / base);
    }

    /**
     * Text appended after a task name in the Android task list.
     * Budgeted: all-time total + percent, e.g. {@code "15:00 (75%)"}.
     * Non-budgeted: today's worked time, e.g. {@code "1:30"}.
     */
    public static String suffix(int todayMinutes, int allTimeMinutes, Integer budgetMinutes) {
        if (hasBudget(budgetMinutes)) {
            return DateTimeUtil.formatDuration(allTimeMinutes)
                    + " (" + percent(allTimeMinutes, budgetMinutes) + "%)";
        }
        return DateTimeUtil.formatDuration(todayMinutes);
    }
}
