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
package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;
import android.content.SharedPreferences;

import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.options.Key;
import org.zephyrsoft.trackworktime.timer.TimerManager;
import org.zephyrsoft.trackworktime.util.Updatable;

/**
 * Keeps the TWT Control watchapp's status + recent-task list live by re-sending them on every
 * tracking change, regardless of origin (phone UI, watchface, geofence, ...). Mirrors
 * {@link PebbleStatusPusher} (which does the same for the watchface) and is registered as a
 * {@link TimerManager} listener. Without this, TWT Control only refreshed in reply to its own
 * commands or on app-open, so a start/stop made elsewhere left its display stale.
 *
 * <p>Gated by the same {@link Key#STATUS_ON_PEBBLE} preference as the watchface push. Command
 * replies in {@code PebbleListenerService} stay ungated, so the control app still works when the
 * status feature is off.
 */
public class TwtControlPusher implements Updatable {

    /** See {@link PebbleStatusPusher#update()} — collapses the notify burst into one push. */
    private static final long COALESCE_DELAY_MS = 300;

    private final Context context;
    private final SharedPreferences preferences;
    private final TimerManager timerManager;
    private final DAO dao;
    private final CoalescingRunner coalescer;

    public TwtControlPusher(Context context, SharedPreferences preferences,
                            TimerManager timerManager, DAO dao,
                            CoalescingRunner.Scheduler scheduler) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
        this.timerManager = timerManager;
        this.dao = dao;
        this.coalescer = new CoalescingRunner(scheduler, COALESCE_DELAY_MS, this::sendNow);
    }

    @Override
    public void update() {
        // Coalesce the burst and run the (DB-heavy, BLE-blocking) send off the calling thread.
        coalescer.trigger();
    }

    private void sendNow() {
        if (!preferences.getBoolean(Key.STATUS_ON_PEBBLE.getName(), false)) {
            return;
        }
        new TwtControlSender(context, timerManager, dao).sendStatusAndList();
    }
}
