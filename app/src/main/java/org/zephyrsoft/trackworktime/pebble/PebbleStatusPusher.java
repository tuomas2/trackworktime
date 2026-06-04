package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;
import android.content.SharedPreferences;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.PeriodEnum;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.options.Key;
import org.zephyrsoft.trackworktime.timer.TimerManager;
import org.zephyrsoft.trackworktime.util.Updatable;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Pushes TWT status to the watchface on every tracking change. Registered as a TimerManager
 * listener (fires from notifyListeners() for every created event, regardless of origin).
 */
public class PebbleStatusPusher implements Updatable {

    private final SharedPreferences preferences;
    private final TimerManager timerManager;
    private final DAO dao;
    private final PebbleStatusSender sender;

    public PebbleStatusPusher(Context context, SharedPreferences preferences,
                              TimerManager timerManager, DAO dao) {
        this.preferences = preferences;
        this.timerManager = timerManager;
        this.dao = dao;
        this.sender = new PebbleStatusSender(context);
    }

    @Override
    public void update() {
        pushStatus();
    }

    /** Recompute the current status and send it to the watchface (no-op if the option is off). */
    public void pushStatus() {
        try {
            if (!preferences.getBoolean(Key.STATUS_ON_PEBBLE.getName(), false)) {
                return;
            }
            Event latest = dao.getLastEventUpTo(OffsetDateTime.now());
            boolean tracking = latest != null
                    && latest.getType().equals(TypeEnum.CLOCK_IN.getValue());

            Task current = timerManager.getCurrentTask();
            int taskId = current != null ? current.getId() : 0;
            String taskName = current != null ? current.getName() : "";

            int totalWorkedTodayMin = (int) timerManager.calculateTimeSum(LocalDate.now(), PeriodEnum.DAY);
            long segmentStartEpoch = tracking ? latest.getDateTime().toEpochSecond() : 0L;
            long nowEpoch = System.currentTimeMillis() / 1000L;

            PebbleStatus status = PebbleStatus.of(
                    tracking, taskId, taskName, totalWorkedTodayMin, segmentStartEpoch, nowEpoch);
            sender.send(status);
        } catch (Exception e) {
            Logger.warn(e, "failed to push TWT status to Pebble");
        }
    }
}
