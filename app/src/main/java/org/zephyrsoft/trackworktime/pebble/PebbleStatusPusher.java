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

    /**
     * How long to wait after the last tracking-change notification before actually pushing. A
     * single start/stop/switch fans out into up to six {@code notifyListeners()} passes; this
     * window collapses the whole burst into one push of the final state. See {@link CoalescingRunner}.
     */
    private static final long COALESCE_DELAY_MS = 300;

    private final SharedPreferences preferences;
    private final TimerManager timerManager;
    private final DAO dao;
    private final PebbleStatusSender sender;
    private final CoalescingRunner coalescer;

    /**
     * Last status actually handed to the sender, or null when the next push must go out
     * unconditionally. Guards against waking the watchface over Bluetooth with a payload
     * identical to the one it already has: this pusher is on the per-minute watchdog path
     * ({@code Constants.REPEAT_TIME} → {@code notifyListeners()}), so without it the watch
     * received ~1440 identical 10-key messages a day — each of which also cost flash writes
     * on the watch. {@link PebbleStatus} is time-invariant while tracking continues, so an
     * unchanged minute really does produce an equal snapshot.
     * <p>
     * Accessed only from the coalescer's scheduler thread and from {@link #pushStatusForced}
     * (the watchapp-opened callback), hence volatile.
     */
    private volatile PebbleStatus lastSent;

    public PebbleStatusPusher(Context context, SharedPreferences preferences,
                              TimerManager timerManager, DAO dao,
                              CoalescingRunner.Scheduler scheduler) {
        this.preferences = preferences;
        this.timerManager = timerManager;
        this.dao = dao;
        this.sender = new PebbleStatusSender(context);
        this.coalescer = new CoalescingRunner(scheduler, COALESCE_DELAY_MS, this::pushStatus);
    }

    @Override
    public void update() {
        // Coalesce the burst and run the (DB-heavy, BLE-blocking) push off the calling thread.
        coalescer.trigger();
    }

    /**
     * Recompute the current status and send it to the watchface (no-op if the option is off, or
     * if the resulting status is identical to the last one sent).
     */
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
            Map<Integer, Integer> perTaskGross = PebbleTaskTimes.todayByTaskId(dao, timerManager);
            // gross day total on the same basis as the GROSS per-task values (no auto-pause
            // deduction), so the watch can compute the unbudgeted task percent gross/gross -> sum
            // before the auto-pause deduction below.
            int dayGrossTodayMin = 0;
            for (int minutes : perTaskGross.values()) {
                dayGrossTodayMin += minutes;
            }
            // Net per-task: attribute today's not-yet-persisted auto-pause to the task that spanned
            // the lunch, so the task row matches the day total (and TWT Control).
            Map<Integer, Integer> perTask = PebbleTaskTimes.deductTodayAutoPause(perTaskGross, dao, timerManager);
            int taskWorkedTodayMin = (tracking && perTask.containsKey(taskId)) ? perTask.get(taskId) : 0;
            // Only scan all-time history when the current task actually has a budget.
            int taskAllTimeMin = 0;
            if (tracking && taskBudgetMin > 0) {
                Map<Integer, Integer> perTaskAllTime = PebbleTaskTimes.deductTodayAutoPause(
                        PebbleTaskTimes.allTimeByTaskId(dao, timerManager), dao, timerManager);
                taskAllTimeMin = perTaskAllTime.getOrDefault(taskId, 0);
            }
            long segmentStartEpoch = tracking ? latest.getDateTime().toEpochSecond() : 0L;
            long nowEpoch = System.currentTimeMillis() / 1000L;
            int dailyTargetMin = timerManager.getDailyWorkTimeTarget(LocalDate.now().getDayOfWeek());

            PebbleStatus status = PebbleStatus.of(
                    tracking, taskId, taskName, totalWorkedTodayMin, taskWorkedTodayMin,
                    segmentStartEpoch, nowEpoch, dailyTargetMin, taskAllTimeMin, taskBudgetMin,
                    dayGrossTodayMin);
            if (status.equals(lastSent)) {
                // identical payload -- the watch already shows exactly this, so sending it would
                // only cost a BLE wakeup on both ends plus flash writes on the watch
                return;
            }
            sender.send(status);
            lastSent = status;
        } catch (Exception e) {
            // do NOT update lastSent here: a failed send means the watch may not have the value,
            // so the next push must be allowed through
            Logger.warn(e, "failed to push TWT status to Pebble");
        }
    }

    /**
     * Push unconditionally, ignoring the dedupe. Used when a watchapp is opened: the watchface
     * is relaunched whenever any watchapp runs, and it restores its strip from its own persisted
     * copy, but we cannot know that copy is current (it may predate changes made while the
     * watchface was not running), so the freshest state is always re-sent.
     */
    public void pushStatusForced() {
        lastSent = null;
        pushStatus();
    }
}
