package org.zephyrsoft.trackworktime.pebble;

import java.util.UUID;

/**
 * AppMessage integer keys, shared with the TimeStyle watchface.
 *
 * IMPORTANT: these integers MUST match build/js/message_keys.json in the TimeStyle project
 * after the TWT keys are appended to package.json. They are now PINNED to the real values
 * produced by the watchface build (no longer placeholders); update them only if the watchface's
 * message_keys.json reassigns the TWT keys.
 */
public final class TwtPebbleKeys {

    private TwtPebbleKeys() {}

    /** TimeStyle TWT watchface UUID (our fork; see TimeStylePebble/package.json).
     *  Distinct from upstream TimeStyle (4368ffa4-...) so the fork can be published
     *  without colliding. */
    public static final UUID TIMESTYLE_UUID =
            UUID.fromString("812efd3c-309b-4474-a942-35bcb55a76f2");

    public static final int TWT_IS_TRACKING       = 10030;
    public static final int TWT_TASK_ID           = 10031;
    public static final int TWT_TASK_NAME         = 10032;
    public static final int TWT_WORKED_BEFORE_MIN = 10033;
    public static final int TWT_SEGMENT_START     = 10034;
    // Appended at the END of TimeStyle package.json messageKeys (a later watch task);
    // value verified against build/js/message_keys.json. Appended (not inserted) so the
    // MIDI/Setting/Elec keys keep their pinned integers.
    public static final int TWT_TASK_WORKED_BEFORE_MIN = 10042;
}
