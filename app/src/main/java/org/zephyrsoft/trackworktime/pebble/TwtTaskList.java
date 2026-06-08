package org.zephyrsoft.trackworktime.pebble;

import java.util.List;

/** Encodes recent tasks into the control app's TASK_LIST wire format: "id\tname\tmin\nid\tname\tmin". */
public final class TwtTaskList {

    private TwtTaskList() {}

    public static final class Item {
        public final int id;
        public final String name;
        public final int minutes;
        public Item(int id, String name, int minutes) {
            this.id = id;
            this.name = name;
            this.minutes = minutes;
        }
    }

    public static String encode(List<Item> items, int max) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(items.size(), max);
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            String name = (it.name == null ? "" : it.name).replace('\t', ' ').replace('\n', ' ');
            if (name.length() > PebbleStatus.MAX_TASK_NAME_LEN) {
                name = name.substring(0, PebbleStatus.MAX_TASK_NAME_LEN);
            }
            if (i > 0) sb.append('\n');
            sb.append(it.id).append('\t').append(name).append('\t').append(it.minutes);
        }
        return sb.toString();
    }
}
