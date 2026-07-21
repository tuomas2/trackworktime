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

import android.os.Handler;
import android.os.HandlerThread;

/**
 * Production {@link CoalescingRunner.Scheduler} backed by a single background {@link HandlerThread}.
 * All Pebble pushes are both coalesced and moved off the calling (often main/UI) thread, so the
 * heavy status build (several full-history DB scans) and the blocking Core-app service bind no
 * longer jank the UI or serialize the {@code notifyListeners()} burst.
 *
 * <p>App-scoped: one instance lives for the whole process (created in {@code Basics}); the thread is
 * never quit.
 */
public final class HandlerScheduler implements CoalescingRunner.Scheduler {

    private final Handler handler;

    public HandlerScheduler() {
        HandlerThread thread = new HandlerThread("pebble-push");
        thread.start();
        this.handler = new Handler(thread.getLooper());
    }

    @Override
    public void postDelayed(Runnable r, long delayMs) {
        handler.postDelayed(r, delayMs);
    }

    @Override
    public void removeCallbacks(Runnable r) {
        handler.removeCallbacks(r);
    }
}
