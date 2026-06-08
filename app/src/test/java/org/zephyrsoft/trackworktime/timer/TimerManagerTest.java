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
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.util.Updatable;

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
}
