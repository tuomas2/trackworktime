package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TwtTaskListTest {

    @Test
    public void encodesIdNameMinutesBudgetAndDayPercentPerLine() {
        String s = TwtTaskList.encode(Arrays.asList(
                new TwtTaskList.Item(5, "Alpha", 0, -1, -1),
                new TwtTaskList.Item(8, "Beta", 900, 75, 40)), 7);
        assertThat(s).isEqualTo("5\tAlpha\t0\t-1\t-1\n8\tBeta\t900\t75\t40");
    }

    @Test
    public void capsAtMax() {
        TwtTaskList.Item[] items = new TwtTaskList.Item[10];
        for (int i = 0; i < 10; i++) items[i] = new TwtTaskList.Item(i, "T" + i, i, -1, 10);
        String s = TwtTaskList.encode(Arrays.asList(items), 3);
        assertThat(s.split("\n")).hasLength(3);
    }

    @Test
    public void stripsTabAndNewlineFromNames() {
        String s = TwtTaskList.encode(
                List.of(new TwtTaskList.Item(1, "a\tb\nc", 5, -1, 7)), 7);
        assertThat(s).isEqualTo("1\ta b c\t5\t-1\t7");
    }

    @Test
    public void emptyListIsEmptyString() {
        assertThat(TwtTaskList.encode(List.of(), 7)).isEmpty();
    }

    @Test
    public void truncatesLongNamesTo32Chars() {
        String longName = "0123456789012345678901234567890123456789"; // 40 chars
        String s = TwtTaskList.encode(
                List.of(new TwtTaskList.Item(3, longName, 12, -1, 0)), 7);
        assertThat(s).isEqualTo("3\t01234567890123456789012345678901\t12\t-1\t0"); // 32-char name
    }
}
