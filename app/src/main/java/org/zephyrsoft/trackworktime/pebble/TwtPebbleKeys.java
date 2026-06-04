package org.zephyrsoft.trackworktime.pebble;

import java.util.UUID;

/**
 * AppMessage integer keys, shared with the TimeStyle watchface.
 *
 * IMPORTANT: these integers MUST match build/js/message_keys.json in the TimeStyle project
 * after the TWT keys are appended to package.json. They are PLACEHOLDERS here and are pinned
 * to the real values in a later task once the watchface is built. Do not "fix" them now.
 */
public final class TwtPebbleKeys {

    private TwtPebbleKeys() {}

    /** TimeStyle watchface UUID (from TimeStylePebble/package.json). */
    public static final UUID TIMESTYLE_UUID =
            UUID.fromString("4368ffa4-f0fb-4823-90be-f754b076bdaa");

    public static final int TWT_IS_TRACKING       = 10030;
    public static final int TWT_TASK_ID           = 10031;
    public static final int TWT_TASK_NAME         = 10032;
    public static final int TWT_WORKED_BEFORE_MIN = 10033;
    public static final int TWT_SEGMENT_START     = 10034;
}
