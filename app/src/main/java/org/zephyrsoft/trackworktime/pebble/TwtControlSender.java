package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.model.PeriodEnum;
import org.zephyrsoft.trackworktime.timer.TimerManager;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Builds and sends the current status + recent-task list to the TWT Control watchapp. */
public final class TwtControlSender {

    private static final int MAX_TASKS = 7;

    private final Context context;
    private final TimerManager timerManager;
    private final DAO dao;

    public TwtControlSender(Context context, TimerManager timerManager, DAO dao) {
        this.context = context.getApplicationContext();
        this.timerManager = timerManager;
        this.dao = dao;
    }

    public void sendStatusAndList() {
        try {
            Event latest = dao.getLastEventUpTo(OffsetDateTime.now());
            boolean tracking = latest != null
                    && latest.getType().equals(TypeEnum.CLOCK_IN.getValue());
            String taskName = "";
            if (tracking && latest.getTask() != null) {
                Task t = dao.getTask(latest.getTask());
                if (t != null) taskName = t.getName();
            }
            int workedMin = (int) timerManager.calculateTimeSum(LocalDate.now(), PeriodEnum.DAY);

            List<TwtTaskList.Item> items = new ArrayList<>();
            for (Task t : dao.getActiveTasksSortedByLastUsed()) {
                items.add(new TwtTaskList.Item(t.getId(), t.getName()));
            }
            String list = TwtTaskList.encode(items, MAX_TASKS);

            PebbleDictionary dict = new PebbleDictionary();
            dict.addUint8(TwtControlKeys.ST_TRACKING, (byte) (tracking ? 1 : 0));
            dict.addString(TwtControlKeys.ST_TASK_NAME, taskName);
            dict.addInt32(TwtControlKeys.ST_WORKED_MIN, workedMin);
            dict.addString(TwtControlKeys.TASK_LIST, list);
            PebbleKit.sendDataToPebble(context, TwtControlKeys.CONTROL_UUID, dict);
        } catch (Exception e) {
            Logger.warn(e, "failed to send status/list to TWT Control");
        }
    }
}
