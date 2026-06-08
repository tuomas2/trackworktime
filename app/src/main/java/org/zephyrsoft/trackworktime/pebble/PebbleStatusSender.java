package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import io.rebble.pebblekit2.common.model.PebbleDictionaryItem;

import org.pmw.tinylog.Logger;

import java.util.HashMap;
import java.util.Map;

/** Serialises a {@link PebbleStatus} and sends it to the TimeStyle watchface. */
public final class PebbleStatusSender {

    private final Context context;

    public PebbleStatusSender(Context context) {
        this.context = context.getApplicationContext();
    }

    public void send(PebbleStatus status) {
        try {
            Map<Integer, PebbleDictionaryItem> dict = new HashMap<>();
            dict.put(TwtPebbleKeys.TWT_IS_TRACKING,
                    new PebbleDictionaryItem.UInt8(status.isTracking() ? 1 : 0));
            dict.put(TwtPebbleKeys.TWT_TASK_ID, new PebbleDictionaryItem.Int32(status.taskId()));
            dict.put(TwtPebbleKeys.TWT_TASK_NAME, new PebbleDictionaryItem.Text(status.taskName()));
            dict.put(TwtPebbleKeys.TWT_WORKED_BEFORE_MIN,
                    new PebbleDictionaryItem.Int32(status.workedBeforeMin()));
            dict.put(TwtPebbleKeys.TWT_TASK_WORKED_BEFORE_MIN,
                    new PebbleDictionaryItem.Int32(status.taskWorkedBeforeMin()));
            // Epoch seconds sent as int32: the AppMessage field and the watch both use int32,
            // so this silently wraps after 2038-01-19 (Y2038). Accepted for v1.
            dict.put(TwtPebbleKeys.TWT_SEGMENT_START,
                    new PebbleDictionaryItem.Int32((int) status.segmentStartEpoch()));
            PebbleSenders.sendAndClose(context, TwtPebbleKeys.TIMESTYLE_UUID, dict, "TWT watchface status");
        } catch (Exception e) {
            Logger.warn(e, "problem while sending TWT status to Pebble");
        }
    }
}
