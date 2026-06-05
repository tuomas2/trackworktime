package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import io.rebble.pebblekit2.client.java.BaseJavaPebbleListenerService;
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem;
import io.rebble.pebblekit2.common.model.ReceiveResult;

import org.pmw.tinylog.Logger;
import org.zephyrsoft.trackworktime.Basics;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.timer.TimerManager;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * PebbleKit Android 2 listener service. The Pebble/Core app binds to this service (declared in
 * the manifest with the {@code io.rebble.pebblekit2.RECEIVE_DATA_FROM_WATCH} intent filter) and
 * delivers AppMessages from any watchapp; we route by UUID.
 *
 * <p>Replaces the classic {@code PebbleKit.PebbleDataReceiver} broadcast handler, which the Core
 * app does not bridge (it only routes to apps registered via the PebbleKit 2 service/provider).
 */
public final class PebbleListenerService extends BaseJavaPebbleListenerService {

    @Override
    protected void onMessageReceived(UUID watchappUuid,
                                     Map<Integer, ? extends PebbleDictionaryItem> data,
                                     String watchId,
                                     Consumer<ReceiveResult> ackCallback) {
        // Always acknowledge so the watch's AppMessage transaction completes.
        ackCallback.accept(ReceiveResult.Ack.INSTANCE);

        if (!TwtControlKeys.CONTROL_UUID.equals(watchappUuid)) {
            return;  // the watchface (TimeStyle) talks to the phone via its own PKJS, not us
        }
        Logger.debug("PebbleListenerService.onMessageReceived keys={}", data.keySet());
        try {
            Integer cmd = intValue(data.get(TwtControlKeys.CMD));
            if (cmd == null) return;

            Context context = getApplicationContext();
            Basics basics = Basics.get(context);
            TimerManager tm = basics.getTimerManager();
            DAO dao = basics.getDao();

            switch (cmd) {
                case TwtControlKeys.CMD_START: {
                    Integer taskId = intValue(data.get(TwtControlKeys.CMD_TASK_ID));
                    Task task = (taskId != null) ? dao.getTask(taskId) : null;
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

            if (cmd == TwtControlKeys.CMD_START || cmd == TwtControlKeys.CMD_STOP) {
                // Refresh the app's main screen if it's open (matches WifiTracker/LocationTracker
                // behaviour for tracking changes made outside the activity). onMessageReceived
                // runs off the main thread, so post the (UI-touching) refresh to the main looper.
                new android.os.Handler(android.os.Looper.getMainLooper()).post(
                        org.zephyrsoft.trackworktime.WorkTimeTrackerActivity::refreshViewIfShown);
            }

            new TwtControlSender(context, tm, dao).sendStatusAndList();
        } catch (Exception e) {
            Logger.warn(e, "failed to handle TWT Control command");
        }
    }

    /** Push the current status when a relevant watchapp is opened (replaces the connect-receiver). */
    @Override
    protected void onAppOpened(UUID watchappUuid, String watchId) {
        try {
            Context context = getApplicationContext();
            Basics basics = Basics.get(context);
            if (TwtPebbleKeys.TIMESTYLE_UUID.equals(watchappUuid)) {
                basics.getPebbleStatusPusher().pushStatus();
            } else if (TwtControlKeys.CONTROL_UUID.equals(watchappUuid)) {
                new TwtControlSender(context, basics.getTimerManager(), basics.getDao())
                        .sendStatusAndList();
            }
        } catch (Exception e) {
            Logger.warn(e, "failed to push status on Pebble app open");
        }
    }

    private static Integer intValue(PebbleDictionaryItem item) {
        if (item == null) return null;
        Object v = item.getValue();
        return (v instanceof Number) ? ((Number) v).intValue() : null;
    }
}
