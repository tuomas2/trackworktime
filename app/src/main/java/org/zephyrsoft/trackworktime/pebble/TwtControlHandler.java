package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.Basics;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.timer.TimerManager;
import org.zephyrsoft.trackworktime.database.DAO;

/** Receives commands from the TWT Control watchapp and executes them via TimerManager. */
public final class TwtControlHandler extends PebbleKit.PebbleDataReceiver {

    public TwtControlHandler() {
        super(TwtControlKeys.CONTROL_UUID);
    }

    @Override
    public void receiveData(Context context, int transactionId, PebbleDictionary data) {
        PebbleKit.sendAckToPebble(context, transactionId);
        try {
            Long cmd = data.getUnsignedIntegerAsLong(TwtControlKeys.CMD);
            if (cmd == null) return;

            Basics basics = Basics.get(context);
            TimerManager tm = basics.getTimerManager();
            DAO dao = basics.getDao();

            switch (cmd.intValue()) {
                case TwtControlKeys.CMD_START: {
                    Long taskId = data.getInteger(TwtControlKeys.CMD_TASK_ID);
                    Task task = (taskId != null) ? dao.getTask(taskId.intValue()) : null;
                    if (tm.isTracking()) {
                        tm.stopTracking(0, TimerManager.EventOrigin.PEBBLE);
                    }
                    tm.startTracking(0, task, null, TimerManager.EventOrigin.PEBBLE);
                    break;
                }
                case TwtControlKeys.CMD_STOP: {
                    if (tm.isTracking()) {
                        tm.stopTracking(0, TimerManager.EventOrigin.PEBBLE);
                    }
                    break;
                }
                case TwtControlKeys.CMD_REQUEST:
                default:
                    break;
            }

            new TwtControlSender(context, tm, dao).sendStatusAndList();
        } catch (Exception e) {
            Logger.warn(e, "failed to handle TWT Control command");
        }
    }
}
