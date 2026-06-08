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
}
