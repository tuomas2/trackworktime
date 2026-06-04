package org.zephyrsoft.trackworktime.pebble;

import android.content.Context;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import org.pmw.tinylog.Logger;

/** Serialises a {@link PebbleStatus} and sends it to the TimeStyle watchface. */
public class PebbleStatusSender {

    private final Context context;

    public PebbleStatusSender(Context context) {
        this.context = context.getApplicationContext();
    }

    public void send(PebbleStatus status) {
        try {
            PebbleDictionary dict = new PebbleDictionary();
            dict.addUint8(TwtPebbleKeys.TWT_IS_TRACKING, (byte) (status.isTracking() ? 1 : 0));
            dict.addInt32(TwtPebbleKeys.TWT_TASK_ID, status.taskId());
            dict.addString(TwtPebbleKeys.TWT_TASK_NAME, status.taskName());
            dict.addInt32(TwtPebbleKeys.TWT_WORKED_BEFORE_MIN, status.workedBeforeMin());
            dict.addInt32(TwtPebbleKeys.TWT_SEGMENT_START, (int) status.segmentStartEpoch());
            PebbleKit.sendDataToPebble(context, TwtPebbleKeys.TIMESTYLE_UUID, dict);
        } catch (Exception e) {
            Logger.warn(e, "problem while sending TWT status to Pebble");
        }
    }
}
