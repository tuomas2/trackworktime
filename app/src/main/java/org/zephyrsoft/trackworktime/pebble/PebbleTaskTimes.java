package org.zephyrsoft.trackworktime.pebble;

import static org.zephyrsoft.trackworktime.util.DateTimeUtil.truncateEventsToMinute;

import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TimeSum;
import org.zephyrsoft.trackworktime.timer.TimeCalculator;
import org.zephyrsoft.trackworktime.timer.TimerManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minutes worked TODAY (so far) per task id — gross (no auto-pause adjustment), and
 * including the running segment for the currently clocked-in task (because the period
 * end is "now"). Shared by the watchface pusher (current task) and the TWT Control
 * sender (all tasks).
 *
 * <p>The day window is anchored in the configured home time zone and events are
 * truncated to the minute, matching how the day total ({@code TimerManager.calculateTimeSum})
 * and the report screen compute their numbers, so the per-task values track the totals.
 * The remaining (accepted) divergence is gross-vs-net: this sum does not subtract
 * auto-pause, whereas the day total does.
 */
public final class PebbleTaskTimes {

    private PebbleTaskTimes() {}

    /**
     * Return a copy of {@code perTask} with today's not-yet-persisted auto-pause (lunch) subtracted
     * from the task whose segment spans the pause window. While the user is still continuously
     * clocked in across lunch, the break events are not in the DB yet (they are inserted on
     * clock-out), so the gross sums from {@link #todayByTaskId}/{@link #allTimeByTaskId} still
     * include the lunch — exactly the way the day total would, which is why
     * {@code TimeCalculatorV2.calculateNextDay} subtracts it on the fly too. This mirrors that for
     * the per-task numbers so the watchface task row and TWT Control list match the day total (and
     * match what a real CLOCK_OUT/CLOCK_IN break would produce).
     *
     * <p>Kept OUT of the memoized gross sums on purpose: applicability is time-dependent (it only
     * kicks in once "now" is past the pause window, with no new DB event), so baking it into a
     * version-keyed cache would return a stale value. The result may go below zero — the watchface
     * caller folds the running segment back in and depends on that; TWT Control clamps when sending.
     */
    public static Map<Integer, Integer> deductTodayAutoPause(
            Map<Integer, Integer> perTask, DAO dao, TimerManager timerManager) {
        Map<Integer, Integer> out = new HashMap<>(perTask);
        if (!timerManager.isAutoPauseEnabled()) {
            return out;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!timerManager.isAutoPauseApplicable(now)) {
            return out;
        }
        OffsetDateTime begin = now.with(timerManager.getAutoPauseBegin());
        Event lastBeforePause = dao.getLastEventBefore(begin);
        if (lastBeforePause == null || lastBeforePause.getTask() == null) {
            return out;
        }
        int autoPauseMin = (int) timerManager.getAutoPauseDuration();
        out.merge(lastBeforePause.getTask(), -autoPauseMin, Integer::sum);
        return out;
    }

    public static Map<Integer, Integer> todayByTaskId(DAO dao, TimerManager timerManager) {
        ZoneId zone = timerManager.getHomeTimeZone();
        ZonedDateTime begin = LocalDate.now().atStartOfDay(zone);
        OffsetDateTime end = OffsetDateTime.now();
        List<Event> events = dao.getEvents(begin.toInstant(), end.toInstant());
        truncateEventsToMinute(events);
        TimeCalculator timeCalculator = new TimeCalculator(dao, timerManager);
        Map<Task, TimeSum> sums = timeCalculator.calculateSums(begin.toOffsetDateTime(), end, events);
        Map<Integer, Integer> ret = new HashMap<>();
        for (Map.Entry<Task, TimeSum> e : sums.entrySet()) {
            Task task = e.getKey();
            if (task != null && task.getId() != null) {
                ret.put(task.getId(), e.getValue().getAsMinutes());
            }
        }
        return ret;
    }

    // Memoized result keyed on DAO.getDataVersion(): a repeated call with no intervening DB
    // change returns the cached map without rescanning. The watchface pusher always sees a fresh
    // result (the event that triggers it bumped the version); bare refreshes between events reuse
    // the snapshot. Synchronized because pushes can run from different threads.
    private static final Object ALL_TIME_LOCK = new Object();
    private static long allTimeCacheVersion = -1L;
    private static Map<Integer, Integer> allTimeCache = null;

    /**
     * Minutes worked over ALL recorded history per task id — gross (same basis as
     * {@link #todayByTaskId}), including the running segment for the active task (period end is
     * "now"). Used for per-task budget percentages. Memoized (see above) and only ever called by
     * callers that have already confirmed a budget is in play, so the unbudgeted path never pays
     * for the full scan.
     */
    public static Map<Integer, Integer> allTimeByTaskId(DAO dao, TimerManager timerManager) {
        long version = dao.getDataVersion();
        synchronized (ALL_TIME_LOCK) {
            if (allTimeCache != null && allTimeCacheVersion == version) {
                return allTimeCache;
            }
        }
        Map<Integer, Integer> immutable =
                java.util.Collections.unmodifiableMap(computeAllTimeByTaskId(dao, timerManager));
        synchronized (ALL_TIME_LOCK) {
            allTimeCache = immutable;
            allTimeCacheVersion = version;
        }
        return immutable;
    }

    private static Map<Integer, Integer> computeAllTimeByTaskId(DAO dao, TimerManager timerManager) {
        Map<Integer, Integer> ret = new HashMap<>();
        List<Event> events = dao.getAllEvents();
        if (events.isEmpty()) {
            return ret;
        }
        truncateEventsToMinute(events);
        // events come back ordered by time ascending, so the first is the earliest.
        OffsetDateTime begin = events.get(0).getDateTime();
        OffsetDateTime end = OffsetDateTime.now();
        TimeCalculator timeCalculator = new TimeCalculator(dao, timerManager);
        Map<Task, TimeSum> sums = timeCalculator.calculateSums(begin, end, events);
        for (Map.Entry<Task, TimeSum> e : sums.entrySet()) {
            Task task = e.getKey();
            if (task != null && task.getId() != null) {
                ret.put(task.getId(), e.getValue().getAsMinutes());
            }
        }
        return ret;
    }
}
