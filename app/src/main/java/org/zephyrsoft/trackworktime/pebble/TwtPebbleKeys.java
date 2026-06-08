package org.zephyrsoft.trackworktime.pebble;

import java.util.UUID;

/**
 * AppMessage integer keys, shared with the TimeStyle watchface.
 *
 * IMPORTANT: these integers MUST match the watchface's auto-assigned AppMessage key IDs
 * (ID = 10000 + position in TimeStylePebble/package.json messageKeys; see the SDK's
 * process_message_keys.py). A drift here is silent and nasty — the watch reads one field as
 * another (it once read dailyTargetMin as taskWorkedBeforeMin). The monorepo script
 * scripts/check-pebble-message-keys.py recomputes the IDs and fails CI/pre-commit on any
 * mismatch; run it after touching either side. Only ever APPEND new messageKeys at the end.
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
    // The two keys below sit at the END of package.json messageKeys, after the
    // MIDI/Setting/Elec keys, so their positions (and IDs) are 10043 and 10044.
    public static final int TWT_TASK_WORKED_BEFORE_MIN = 10043;
    // The DAILY work-time target in minutes (TimerManager.getDailyWorkTimeTarget);
    // 0 when no target is set.
    public static final int TWT_DAILY_TARGET_MIN = 10044;
}
