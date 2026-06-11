package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.timer.TimerManager;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link PebbleTaskTimes#deductTodayAutoPause} subtracts today's not-yet-persisted auto-pause
 * (lunch) from the task whose segment spans the pause window, mirroring the day-total deduction
 * in TimeCalculatorV2.calculateNextDay and what a real CLOCK_OUT/CLOCK_IN break would do. The
 * gross sums themselves stay gross+memoized; this adjustment is time-dependent so it lives here.
 */
public class PebbleTaskTimesTest {

    private static Map<Integer, Integer> map(int taskId, int minutes) {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(taskId, minutes);
        return m;
    }

    /** task 7 was clocked in across lunch (gross 360) -> net 330 after a 30min auto-pause. */
    @Test
    public void deductsAutoPauseFromTheTaskActiveDuringThePause() {
        DAO dao = mock(DAO.class);
        TimerManager tm = mock(TimerManager.class);
        when(tm.isAutoPauseEnabled()).thenReturn(true);
        when(tm.isAutoPauseApplicable(any())).thenReturn(true);
        when(tm.getAutoPauseBegin()).thenReturn(LocalTime.of(11, 30));
        when(tm.getAutoPauseDuration()).thenReturn(30L);
        Event lastBeforePause = mock(Event.class);
        when(lastBeforePause.getTask()).thenReturn(7);
        when(dao.getLastEventBefore(any())).thenReturn(lastBeforePause);

        Map<Integer, Integer> out = PebbleTaskTimes.deductTodayAutoPause(map(7, 360), dao, tm);

        assertThat(out.get(7)).isEqualTo(330);
    }

    @Test
    public void noDeduction_whenAutoPauseDisabled() {
        DAO dao = mock(DAO.class);
        TimerManager tm = mock(TimerManager.class);
        when(tm.isAutoPauseEnabled()).thenReturn(false);

        Map<Integer, Integer> out = PebbleTaskTimes.deductTodayAutoPause(map(7, 360), dao, tm);

        assertThat(out.get(7)).isEqualTo(360);
    }

    /** Already clocked out -> physical auto-pause events exist, gross already excludes lunch. */
    @Test
    public void noDeduction_whenNotApplicable() {
        DAO dao = mock(DAO.class);
        TimerManager tm = mock(TimerManager.class);
        when(tm.isAutoPauseEnabled()).thenReturn(true);
        when(tm.isAutoPauseApplicable(any())).thenReturn(false);

        Map<Integer, Integer> out = PebbleTaskTimes.deductTodayAutoPause(map(7, 360), dao, tm);

        assertThat(out.get(7)).isEqualTo(360);
    }

    @Test
    public void noDeduction_whenTaskActiveDuringPauseHasNoTask() {
        DAO dao = mock(DAO.class);
        TimerManager tm = mock(TimerManager.class);
        when(tm.isAutoPauseEnabled()).thenReturn(true);
        when(tm.isAutoPauseApplicable(any())).thenReturn(true);
        when(tm.getAutoPauseBegin()).thenReturn(LocalTime.of(11, 30));
        Event lastBeforePause = mock(Event.class);
        when(lastBeforePause.getTask()).thenReturn(null);
        when(dao.getLastEventBefore(any())).thenReturn(lastBeforePause);

        Map<Integer, Integer> out = PebbleTaskTimes.deductTodayAutoPause(map(7, 360), dao, tm);

        assertThat(out.get(7)).isEqualTo(360);
    }

    /** Only the lunch-window task is touched; other tasks are passed through untouched. */
    @Test
    public void leavesOtherTasksUntouched() {
        DAO dao = mock(DAO.class);
        TimerManager tm = mock(TimerManager.class);
        when(tm.isAutoPauseEnabled()).thenReturn(true);
        when(tm.isAutoPauseApplicable(any())).thenReturn(true);
        when(tm.getAutoPauseBegin()).thenReturn(LocalTime.of(11, 30));
        when(tm.getAutoPauseDuration()).thenReturn(30L);
        Event lastBeforePause = mock(Event.class);
        when(lastBeforePause.getTask()).thenReturn(7);
        when(dao.getLastEventBefore(any())).thenReturn(lastBeforePause);
        Map<Integer, Integer> in = new HashMap<>();
        in.put(7, 360);
        in.put(3, 90);

        Map<Integer, Integer> out = PebbleTaskTimes.deductTodayAutoPause(in, dao, tm);

        assertThat(out.get(7)).isEqualTo(330);
        assertThat(out.get(3)).isEqualTo(90);
    }

    /** Must not mutate the caller's map (allTimeByTaskId hands back an unmodifiable cached map). */
    @Test
    public void doesNotMutateInputMap() {
        DAO dao = mock(DAO.class);
        TimerManager tm = mock(TimerManager.class);
        when(tm.isAutoPauseEnabled()).thenReturn(true);
        when(tm.isAutoPauseApplicable(any())).thenReturn(true);
        when(tm.getAutoPauseBegin()).thenReturn(LocalTime.of(11, 30));
        when(tm.getAutoPauseDuration()).thenReturn(30L);
        Event lastBeforePause = mock(Event.class);
        when(lastBeforePause.getTask()).thenReturn(7);
        when(dao.getLastEventBefore(any())).thenReturn(lastBeforePause);
        Map<Integer, Integer> in = java.util.Collections.unmodifiableMap(map(7, 360));

        Map<Integer, Integer> out = PebbleTaskTimes.deductTodayAutoPause(in, dao, tm);

        assertThat(out.get(7)).isEqualTo(330);
        assertThat(in.get(7)).isEqualTo(360);
    }
}
