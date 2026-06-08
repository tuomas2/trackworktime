package org.zephyrsoft.trackworktime.pebble;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TwtTaskListTest {

    @Test
    public void encodesIdNameAndMinutesPerLine() {
        String s = TwtTaskList.encode(Arrays.asList(
                new TwtTaskList.Item(5, "Alpha", 0),
                new TwtTaskList.Item(8, "Beta", 90)), 7);
        assertThat(s).isEqualTo("5\tAlpha\t0\n8\tBeta\t90");
    }

    @Test
    public void capsAtMax() {
        TwtTaskList.Item[] items = new TwtTaskList.Item[10];
        for (int i = 0; i < 10; i++) items[i] = new TwtTaskList.Item(i, "T" + i, i);
        String s = TwtTaskList.encode(Arrays.asList(items), 3);
        assertThat(s.split("\n")).hasLength(3);
    }

    @Test
    public void stripsTabAndNewlineFromNames() {
        String s = TwtTaskList.encode(
                List.of(new TwtTaskList.Item(1, "a\tb\nc", 5)), 7);
        assertThat(s).isEqualTo("1\ta b c\t5");
    }

    @Test
    public void emptyListIsEmptyString() {
        assertThat(TwtTaskList.encode(List.of(), 7)).isEmpty();
    }
}
