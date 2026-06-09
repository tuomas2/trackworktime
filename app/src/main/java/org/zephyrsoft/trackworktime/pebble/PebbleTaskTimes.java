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
