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

    private final Context context;
    private final SharedPreferences preferences;
    private final TimerManager timerManager;
    private final DAO dao;

    public TwtControlPusher(Context context, SharedPreferences preferences,
                            TimerManager timerManager, DAO dao) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
        this.timerManager = timerManager;
        this.dao = dao;
    }

    @Override
    public void update() {
        if (!preferences.getBoolean(Key.STATUS_ON_PEBBLE.getName(), false)) {
            return;
        }
        new TwtControlSender(context, timerManager, dao).sendStatusAndList();
    }
}
