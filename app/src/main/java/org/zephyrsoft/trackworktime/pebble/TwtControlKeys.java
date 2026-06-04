package org.zephyrsoft.trackworktime.pebble;

import java.util.UUID;

/**
 * AppMessage keys + UUID for the TWT Control watchapp. Integers are PINNED from
 * twt-control/build/js/message_keys.json (verified against build output: CMD=10000,
 * CMD_TASK_ID=10001, ST_TRACKING=10002, ST_TASK_NAME=10003, ST_WORKED_MIN=10004,
 * TASK_LIST=10005). Independent key space from the watchface's TWT_* keys (messages route by UUID).
 */
public final class TwtControlKeys {

    private TwtControlKeys() {}

    public static final UUID CONTROL_UUID =
            UUID.fromString("2B5F824D-533E-40EC-8B77-AE3E28B45B18");

    public static final int CMD          = 10000; // uint8: 1=request, 2=start, 3=stop
    public static final int CMD_TASK_ID  = 10001; // int32

    public static final int CMD_REQUEST = 1;
    public static final int CMD_START   = 2;
    public static final int CMD_STOP    = 3;

    public static final int ST_TRACKING   = 10002; // uint8
    public static final int ST_TASK_NAME  = 10003; // string
    public static final int ST_WORKED_MIN = 10004; // int32
    public static final int TASK_LIST     = 10005; // string "id\tname\nid\tname"
}
