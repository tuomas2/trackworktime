package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import io.rebble.pebblekit2.common.model.PebbleDictionaryItem;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.model.PeriodEnum;
import org.zephyrsoft.trackworktime.timer.TimerManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds and sends the current status + recent-task list to the TWT Control watchapp. */
public final class TwtControlSender {

    private static final int MAX_TASKS = 7;

    private final Context context;
    private final TimerManager timerManager;
    private final DAO dao;

    public TwtControlSender(Context context, TimerManager timerManager, DAO dao) {
        this.context = context.getApplicationContext();
        this.timerManager = timerManager;
        this.dao = dao;
    }

    public void sendStatusAndList() {
        try {
            Event latest = dao.getLastEventUpTo(OffsetDateTime.now());
            boolean tracking = latest != null
                    && latest.getType().equals(TypeEnum.CLOCK_IN.getValue());
            String taskName = "";
            if (tracking && latest.getTask() != null) {
                Task t = dao.getTask(latest.getTask());
                if (t != null) taskName = t.getName();
            }
            int workedMin = (int) timerManager.calculateTimeSum(LocalDate.now(), PeriodEnum.DAY);

            List<Task> activeTasks = dao.getActiveTasksSortedByLastUsed();
            boolean anyBudget = false;
            for (Task t : activeTasks) {
                if (TaskBudget.hasBudget(t.getBudgetMinutes())) { anyBudget = true; break; }
            }
            // Net of today's not-yet-persisted auto-pause (attributed to the task that spanned the
            // lunch), so the per-task list matches the day total and the watchface task row.
            Map<Integer, Integer> perTaskToday = PebbleTaskTimes.deductTodayAutoPause(
                    PebbleTaskTimes.todayByTaskId(dao, timerManager), dao, timerManager);
            // Only pay for the all-time scan when at least one task actually has a budget.
            Map<Integer, Integer> perTaskAllTime = anyBudget
                    ? PebbleTaskTimes.deductTodayAutoPause(
                            PebbleTaskTimes.allTimeByTaskId(dao, timerManager), dao, timerManager)
                    : java.util.Collections.emptyMap();
            List<TwtTaskList.Item> items = new ArrayList<>();
            for (Task t : activeTasks) {
                Integer budget = t.getBudgetMinutes();
                int todayMin = Math.max(0, perTaskToday.getOrDefault(t.getId(), 0));
                int displayMin;
                int percent;
                if (TaskBudget.hasBudget(budget)) {
                    int allTimeMin = Math.max(0, perTaskAllTime.getOrDefault(t.getId(), 0));
                    displayMin = allTimeMin;
                    percent = TaskBudget.percent(allTimeMin, budget);
                } else {
                    displayMin = todayMin;
                    percent = -1;
                }
                // task's share of today's total (-1 while the day total is still 0)
                int dayPercent = TaskBudget.percent(todayMin, workedMin);
                items.add(new TwtTaskList.Item(t.getId(), t.getName(), displayMin, percent, dayPercent));
            }
            String list = TwtTaskList.encode(items, MAX_TASKS);

            Map<Integer, PebbleDictionaryItem> dict = new HashMap<>();
            dict.put(TwtControlKeys.ST_TRACKING, new PebbleDictionaryItem.UInt8(tracking ? 1 : 0));
            dict.put(TwtControlKeys.ST_TASK_NAME, new PebbleDictionaryItem.Text(taskName));
            dict.put(TwtControlKeys.ST_WORKED_MIN, new PebbleDictionaryItem.Int32(workedMin));
            dict.put(TwtControlKeys.TASK_LIST, new PebbleDictionaryItem.Text(list));
            Integer remainMin = timerManager.getMinutesRemaining();
            dict.put(TwtControlKeys.ST_REMAIN_MIN, new PebbleDictionaryItem.Int32(
                    remainMin != null ? remainMin : Integer.MIN_VALUE));
            PebbleSenders.sendAndClose(context, TwtControlKeys.CONTROL_UUID, dict, "TWT Control status/list");
        } catch (Exception e) {
            Logger.warn(e, "failed to send status/list to TWT Control");
        }
    }
}
