/*
 * This file is part of TrackWorkTime (TWT).
 *
 * TWT is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * TWT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License 3.0 for more details.
 *
 * You should have received a copy of the GNU General Public License 3.0
 * along with TWT. If not, see <http://www.gnu.org/licenses/>.
 */
package org.zephyrsoft.trackworktime.timer;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;

import org.junit.Test;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.options.Key;
import org.zephyrsoft.trackworktime.util.Updatable;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TimerManagerTest {

    /**
     * A restore inserts thousands of events one-by-one with origin RESTORE_BACKUP. If each one
     * fires the listeners, the registered Pebble pushers flood the watch with status/list messages
     * (and the UI recalculates per event). Restoring must not notify listeners per event.
     */
    @Test
    public void restoreBackupOrigin_doesNotNotifyListenersPerEvent() {
        DAO dao = mock(DAO.class);
        TimerManager timerManager = new TimerManager(dao, null, null);

        int[] notifyCount = {0};
        Updatable listener = () -> notifyCount[0]++;
        timerManager.addListener(listener);

        OffsetDateTime base = OffsetDateTime.of(2024, 5, 27, 9, 0, 0, 0, ZoneOffset.UTC);
        timerManager.createEvent(base, 1, TypeEnum.CLOCK_IN, "", TimerManager.EventOrigin.RESTORE_BACKUP);
        timerManager.createEvent(base.plusHours(8), null, TypeEnum.CLOCK_OUT, "", TimerManager.EventOrigin.RESTORE_BACKUP);
        timerManager.createEvent(base.plusDays(1), 1, TypeEnum.CLOCK_IN, "", TimerManager.EventOrigin.RESTORE_BACKUP);

        assertThat(notifyCount[0]).isEqualTo(0);
    }

    /**
     * The watch progress percentage divides today's worked time by a daily work-time target.
     * When the user sets an explicit per-day target (Key.WORK_TIME_TARGET_PER_DAY), that value
     * is used verbatim (NOT divided across work days like the weekly Flexi target).
     */
    @Test
    public void dailyWorkTimeTarget_usesExplicitPerDaySettingWhenSet() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getString(eq(Key.WORK_TIME_TARGET_PER_DAY.getName()), anyString()))
                .thenReturn("7:30");
        TimerManager tm = new TimerManager(mock(DAO.class), prefs, null);

        assertThat(tm.getDailyWorkTimeTarget(DayOfWeek.MONDAY)).isEqualTo(450);
    }

    /**
     * When no explicit per-day target is set, fall back to the weekly-Flexi-derived value
     * (getNormalWorkDurationFor) — which is 0 on a non-work day, so the watch hides the percent.
     */
    @Test
    public void dailyWorkTimeTarget_fallsBackToWeeklyDerivedWhenUnset() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getString(eq(Key.WORK_TIME_TARGET_PER_DAY.getName()), anyString()))
                .thenReturn("0:00");
        // all FLEXI_TIME_DAY_* default to false -> isWorkDay false -> getNormalWorkDurationFor == 0
        TimerManager tm = new TimerManager(mock(DAO.class), prefs, null);

        assertThat(tm.getDailyWorkTimeTarget(DayOfWeek.SUNDAY)).isEqualTo(0);
    }

    /**
     * getMinutesRemaining() is the "minutes still to work today" estimate the watch turns into an
     * end-of-workday clock time. Its weekly-balance computation is gated behind Flexi work-day
     * toggles (FLEXI_TIME_DAY_*), so with Flexi DISABLED it used to always return null and the
     * watch estimate vanished for fixed-daily-target users. With Flexi off it must fall back to
     * (explicit per-day target − time worked today): 7:30 target, nothing worked yet -> 450.
     */
    @Test
    public void minutesRemaining_fixedDailyTargetFallbackWhenFlexiDisabled() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(eq(Key.ENABLE_FLEXI_TIME.getName()), anyBoolean())).thenReturn(false);
        when(prefs.getString(eq(Key.FLEXI_TIME_RESET_INTERVAL.getName()), anyString()))
                .thenReturn("NONE");
        when(prefs.getString(eq(Key.WORK_TIME_TARGET_PER_DAY.getName()), anyString()))
                .thenReturn("7:30");
        // no events -> nothing worked today; auto-pause pref defaults to false in the mock
        TimerManager tm = new TimerManager(mock(DAO.class), prefs, null);

        assertThat(tm.getMinutesRemaining()).isEqualTo(450);
    }

    /**
     * With Flexi off and no usable target (explicit 0:00, no weekly-derived value), there is no
     * meaningful end-of-day estimate, so the watch must keep hiding it -> null (not "+overtime").
     */
    @Test
    public void minutesRemaining_nullWhenFlexiDisabledAndNoTarget() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getBoolean(eq(Key.ENABLE_FLEXI_TIME.getName()), anyBoolean())).thenReturn(false);
        when(prefs.getString(eq(Key.FLEXI_TIME_RESET_INTERVAL.getName()), anyString()))
                .thenReturn("NONE");
        when(prefs.getString(eq(Key.WORK_TIME_TARGET_PER_DAY.getName()), anyString()))
                .thenReturn("0:00");
        TimerManager tm = new TimerManager(mock(DAO.class), prefs, null);

        assertThat(tm.getMinutesRemaining()).isNull();
    }
}
