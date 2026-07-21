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

/**
 * Collapses a burst of rapid {@link #trigger()} calls into a single delayed execution of the
 * wrapped action.
 *
 * <p>Motivation: a single tracking change (e.g. a "switch task" command from the watch) fans out
 * through {@code stopTracking()} + {@code startTracking()} into up to six {@code notifyListeners()}
 * passes, each of which used to build the full status and push it to the watch synchronously. That
 * flooded the BLE link with ~13 identical transmissions in ~1.5&nbsp;s, so the watchface showed a
 * stale status for many seconds and the phone UI janked. Coalescing turns that burst into one push
 * of the final state.
 *
 * <p>Each {@link #trigger()} cancels the previously scheduled run and schedules a fresh one
 * {@code delayMs} later, so a burst of N triggers within the window results in exactly one
 * execution (last one wins). The wrapped {@code action} re-reads the current state when it runs, so
 * no state needs to be threaded through — the coalesced run always reflects the latest data.
 *
 * <p>The timing mechanism is injected via {@link Scheduler} so the coalescing logic is unit-testable
 * without an Android {@code Looper}; production uses {@link HandlerScheduler} on a background thread.
 * The SAME {@code action} instance is (re)scheduled every time, which is what lets the scheduler's
 * cancel-by-identity coalesce the burst — do not wrap it in a fresh lambda per trigger.
 */
public final class CoalescingRunner {

    /** Minimal post-delayed / cancel primitive; mirrors {@code android.os.Handler}. */
    public interface Scheduler {
        /** Schedule {@code r} to run after {@code delayMs} milliseconds. */
        void postDelayed(Runnable r, long delayMs);

        /** Cancel a pending {@code r} scheduled via {@link #postDelayed}, if any (by identity). */
        void removeCallbacks(Runnable r);
    }

    private final Scheduler scheduler;
    private final long delayMs;
    private final Runnable action;

    CoalescingRunner(Scheduler scheduler, long delayMs, Runnable action) {
        this.scheduler = scheduler;
        this.delayMs = delayMs;
        this.action = action;
    }

    /** Request a run; coalesces with any pending request so only the last one within the window fires. */
    void trigger() {
        scheduler.removeCallbacks(action);
        scheduler.postDelayed(action, delayMs);
    }

    /** Drop any pending run without executing it. */
    void cancel() {
        scheduler.removeCallbacks(action);
    }
}
