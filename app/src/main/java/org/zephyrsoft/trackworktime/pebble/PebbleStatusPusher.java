package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;
import android.content.SharedPreferences;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.PeriodEnum;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.options.Key;
import org.zephyrsoft.trackworktime.timer.TimerManager;
import org.zephyrsoft.trackworktime.util.Updatable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Pushes TWT status to the watchface on every tracking change. Registered as a TimerManager
 * listener (fires from notifyListeners() for every created event, regardless of origin).
 */
public class PebbleStatusPusher implements Updatable {

    private final SharedPreferences preferences;
    private final TimerManager timerManager;
    private final DAO dao;
    private final PebbleStatusSender sender;

    public PebbleStatusPusher(Context context, SharedPreferences preferences,
                              TimerManager timerManager, DAO dao) {
        this.preferences = preferences;
        this.timerManager = timerManager;
        this.dao = dao;
        this.sender = new PebbleStatusSender(context);
    }

    @Override
    public void update() {
        pushStatus();
    }

    /** Recompute the current status and send it to the watchface (no-op if the option is off). */
    public void pushStatus() {
        try {
            if (!preferences.getBoolean(Key.STATUS_ON_PEBBLE.getName(), false)) {
                return;
            }
            Event latest = dao.getLastEventUpTo(OffsetDateTime.now());
            boolean tracking = latest != null
                    && latest.getType().equals(TypeEnum.CLOCK_IN.getValue());

            // Derive the task from the SAME latest event as the tracking flag, so the snapshot
            // is internally consistent (avoids a transient tracking=true / task=null at the
            // exact event-creation instant that calling getCurrentTask() separately could cause).
            int taskId = 0;
            String taskName = "";
            int taskBudgetMin = 0;
            if (tracking && latest.getTask() != null) {
                taskId = latest.getTask();
                Task task = dao.getTask(latest.getTask());
                if (task != null) {
                    taskName = task.getName();
                    if (task.getBudgetMinutes() != null) {
                        taskBudgetMin = task.getBudgetMinutes();
                    }
                }
            }

            int totalWorkedTodayMin = (int) timerManager.calculateTimeSum(LocalDate.now(), PeriodEnum.DAY);
            Map<Integer, Integer> perTask = PebbleTaskTimes.todayByTaskId(dao, timerManager);
            int taskWorkedTodayMin = (tracking && perTask.containsKey(taskId)) ? perTask.get(taskId) : 0;
            // gross day total on the same basis as the per-task values (no auto-pause deduction),
            // so the watch can compute the unbudgeted task percent gross/gross
            int dayGrossTodayMin = 0;
            for (int minutes : perTask.values()) {
                dayGrossTodayMin += minutes;
            }
            // Only scan all-time history when the current task actually has a budget.
            int taskAllTimeMin = 0;
            if (tracking && taskBudgetMin > 0) {
                Map<Integer, Integer> perTaskAllTime = PebbleTaskTimes.allTimeByTaskId(dao, timerManager);
                taskAllTimeMin = perTaskAllTime.getOrDefault(taskId, 0);
            }
            long segmentStartEpoch = tracking ? latest.getDateTime().toEpochSecond() : 0L;
            long nowEpoch = System.currentTimeMillis() / 1000L;
            int dailyTargetMin = timerManager.getDailyWorkTimeTarget(LocalDate.now().getDayOfWeek());

            PebbleStatus status = PebbleStatus.of(
                    tracking, taskId, taskName, totalWorkedTodayMin, taskWorkedTodayMin,
                    segmentStartEpoch, nowEpoch, dailyTargetMin, taskAllTimeMin, taskBudgetMin,
                    dayGrossTodayMin);
            sender.send(status);
        } catch (Exception e) {
            Logger.warn(e, "failed to push TWT status to Pebble");
        }
    }
}
