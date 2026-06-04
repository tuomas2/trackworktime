package org.zephyrsoft.trackworktime.pebble;

import java.util.List;

/** Encodes recent tasks into the control app's TASK_LIST wire format: "id\tname\nid\tname". */
public final class TwtTaskList {

    private TwtTaskList() {}

    public static final class Item {
        public final int id;
        public final String name;
        public Item(int id, String name) { this.id = id; this.name = name; }
    }

    public static String encode(List<Item> items, int max) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(items.size(), max);
        for (int i = 0; i < n; i++) {
            Item it = items.get(i);
            String name = (it.name == null ? "" : it.name).replace('\t', ' ').replace('\n', ' ');
            if (i > 0) sb.append('\n');
            sb.append(it.id).append('\t').append(name);
        }
        return sb.toString();
    }
}
