package org.zephyrsoft.trackworktime.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.util.TaskInputResolver.Kind;
import org.zephyrsoft.trackworktime.util.TaskInputResolver.Resolution;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TaskInputResolverTest {

    private static Task active(int id, String name) {
        return new Task(id, name, 1, 0, 0);
    }

    private static Task inactive(int id, String name) {
        return new Task(id, name, 0, 0, 0);
    }

    @Test
    public void blankOrNullYieldsNone() {
        List<Task> tasks = Collections.singletonList(active(1, "Coding"));
        assertThat(TaskInputResolver.resolve(null, tasks).kind).isEqualTo(Kind.NONE);
        assertThat(TaskInputResolver.resolve("", tasks).kind).isEqualTo(Kind.NONE);
        assertThat(TaskInputResolver.resolve("   ", tasks).kind).isEqualTo(Kind.NONE);
    }

    @Test
    public void exactActiveNameUsesExisting() {
        Task coding = active(1, "Coding");
        Resolution r = TaskInputResolver.resolve("Coding", Arrays.asList(coding));
        assertThat(r.kind).isEqualTo(Kind.USE_EXISTING);
        assertThat(r.task).isEqualTo(coding);
    }

    @Test
    public void differentCaseActiveNameUsesExisting() {
        Task coding = active(1, "Coding");
        Resolution r = TaskInputResolver.resolve("  cODInG ", Arrays.asList(coding));
        assertThat(r.kind).isEqualTo(Kind.USE_EXISTING);
        assertThat(r.task).isEqualTo(coding);
    }

    @Test
    public void inactiveSameNameReactivates() {
        Task old = inactive(2, "Meeting");
        Resolution r = TaskInputResolver.resolve("meeting", Arrays.asList(old));
        assertThat(r.kind).isEqualTo(Kind.REACTIVATE);
        assertThat(r.task).isEqualTo(old);
    }

    @Test
    public void unknownNameCreatesWithTrimmedName() {
        Resolution r = TaskInputResolver.resolve("  Research ", Arrays.asList(active(1, "Coding")));
        assertThat(r.kind).isEqualTo(Kind.CREATE);
        assertThat(r.newName).isEqualTo("Research");
    }

    @Test
    public void activeWinsOverInactiveWithSameName() {
        Task activeDup = active(1, "Support");
        Task inactiveDup = inactive(2, "Support");
        Resolution r = TaskInputResolver.resolve("support", Arrays.asList(inactiveDup, activeDup));
        assertThat(r.kind).isEqualTo(Kind.USE_EXISTING);
        assertThat(r.task).isEqualTo(activeDup);
    }
}
