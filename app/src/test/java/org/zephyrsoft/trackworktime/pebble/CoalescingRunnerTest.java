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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the core guarantee that makes the Pebble-push flood go away: a burst of rapid
 * {@link CoalescingRunner#trigger()} calls collapses into exactly one execution of the wrapped
 * action.
 */
public class CoalescingRunnerTest {

    /**
     * A fake {@link CoalescingRunner.Scheduler} that models {@code android.os.Handler}'s
     * cancel-by-identity semantics: at most one copy of a given runnable is pending, and
     * {@link #removeCallbacks} drops it. {@link #runPending()} fires whatever is queued.
     */
    private static final class FakeScheduler implements CoalescingRunner.Scheduler {
        final List<Runnable> pending = new ArrayList<>();
        int postCount = 0;
        int removeCount = 0;

        @Override
        public void postDelayed(Runnable r, long delayMs) {
            postCount++;
            pending.add(r);
        }

        @Override
        public void removeCallbacks(Runnable r) {
            removeCount++;
            // Handler.removeCallbacks removes ALL pending posts of this exact runnable instance.
            pending.removeIf(p -> p == r);
        }

        void runPending() {
            List<Runnable> toRun = new ArrayList<>(pending);
            pending.clear();
            for (Runnable r : toRun) {
                r.run();
            }
        }
    }

    @Test
    public void burstOfTriggersCollapsesToSingleExecution() {
        FakeScheduler scheduler = new FakeScheduler();
        int[] runs = {0};
        CoalescingRunner runner = new CoalescingRunner(scheduler, 300, () -> runs[0]++);

        // Simulate the six notifyListeners() passes a single "switch task" command produces.
        for (int i = 0; i < 6; i++) {
            runner.trigger();
        }

        // Nothing runs until the delay elapses...
        assertEquals("action must not run synchronously on trigger()", 0, runs[0]);
        // ...and exactly one run is queued (each trigger cancelled the previous post).
        assertEquals("burst must collapse to a single pending run", 1, scheduler.pending.size());

        scheduler.runPending();
        assertEquals("a burst of 6 triggers must execute the action exactly once", 1, runs[0]);
    }

    @Test
    public void separatedTriggersEachRunOnceTheQueueDrainsBetween() {
        FakeScheduler scheduler = new FakeScheduler();
        int[] runs = {0};
        CoalescingRunner runner = new CoalescingRunner(scheduler, 300, () -> runs[0]++);

        runner.trigger();
        scheduler.runPending();     // window elapsed, first push sent
        runner.trigger();
        scheduler.runPending();     // a later, separate change

        assertEquals("two changes separated by more than the window run twice", 2, runs[0]);
    }

    @Test
    public void cancelDropsPendingRun() {
        FakeScheduler scheduler = new FakeScheduler();
        int[] runs = {0};
        CoalescingRunner runner = new CoalescingRunner(scheduler, 300, () -> runs[0]++);

        runner.trigger();
        runner.cancel();
        scheduler.runPending();

        assertEquals("a cancelled run must not execute", 0, runs[0]);
        assertEquals(0, scheduler.pending.size());
    }
}
