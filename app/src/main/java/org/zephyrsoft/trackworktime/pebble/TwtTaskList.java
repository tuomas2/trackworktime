package org.zephyrsoft.trackworktime.pebble;

import java.util.List;

/**
 * Encodes recent tasks into the control app's TASK_LIST wire format:
 * "id\tname\tdisplayMin\tpercent\tdayPercent" per line. {@code percent} is {@code -1} when the
 * task has no budget; otherwise it is the rounded percent of budget (may exceed 100).
 * {@code dayPercent} is the task's share of today's total worked time, {@code -1} when nothing
 * has been worked today. {@code displayMin} is today's time for non-budgeted tasks and the
 * all-time total for budgeted tasks (decided by the sender). An old watchapp build only reads
 * the first four fields (its atoi stops at the tab), so appending dayPercent is compatible.
 */
public final class TwtTaskList {

    private TwtTaskList() {}

    public static final class Item {
        public final int id;
        public final String name;
        public final int minutes;
        public final int percent;
        public final int dayPercent;
        public Item(int id, String name, int minutes, int percent, int dayPercent) {
            this.id = id;
            this.name = name;
            this.minutes = minutes;
            this.percent = percent;
            this.dayPercent = dayPercent;
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
            sb.append(it.id).append('\t').append(name).append('\t')
              .append(it.minutes).append('\t').append(it.percent)
              .append('\t').append(it.dayPercent);
        }
        return sb.toString();
    }
}
