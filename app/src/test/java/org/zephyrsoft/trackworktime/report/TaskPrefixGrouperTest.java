package org.zephyrsoft.trackworktime.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TimeSum;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class TaskPrefixGrouperTest {

	private static Task task(int id, String name) {
		return new Task(id, name, 1, id, 0);
	}

	private static TimeSum timeSum(int hours, int minutes) {
		TimeSum sum = new TimeSum();
		sum.set(hours, minutes);
		return sum;
	}

	@Test
	public void prefixOfReturnsFirstWord() {
		assertEquals("vrt", TaskPrefixGrouper.prefixOf("vrt foo"));
		assertEquals("vrt", TaskPrefixGrouper.prefixOf("  vrt foo bar  "));
		assertEquals("lunch", TaskPrefixGrouper.prefixOf("lunch"));
		assertEquals("", TaskPrefixGrouper.prefixOf("   "));
		assertEquals("", TaskPrefixGrouper.prefixOf(null));
	}

	@Test
	public void groupByPrefixMergesSameFirstWord() {
		Map<Task, TimeSum> sums = new HashMap<>();
		sums.put(task(1, "vrt foo"), timeSum(1, 30));
		sums.put(task(2, "vrt bar"), timeSum(2, 45));
		sums.put(task(3, "ab jotain"), timeSum(0, 15));

		Map<String, TimeSum> grouped = TaskPrefixGrouper.groupByPrefix(sums);

		assertEquals(2, grouped.size());
		assertEquals("4:15", grouped.get("vrt").toString());
		assertEquals("0:15", grouped.get("ab").toString());
	}

	@Test
	public void groupByPrefixMergesSingleWordNameWithPrefixedName() {
		Map<Task, TimeSum> sums = new HashMap<>();
		sums.put(task(1, "vrt"), timeSum(1, 0));
		sums.put(task(2, "vrt foo"), timeSum(0, 30));

		Map<String, TimeSum> grouped = TaskPrefixGrouper.groupByPrefix(sums);

		assertEquals(1, grouped.size());
		assertEquals("1:30", grouped.get("vrt").toString());
	}

	@Test
	public void groupByPrefixHandlesNullTaskAndEmptyMap() {
		assertTrue(TaskPrefixGrouper.groupByPrefix(new HashMap<>()).isEmpty());

		Map<Task, TimeSum> sums = new HashMap<>();
		sums.put(null, timeSum(0, 20));
		Map<String, TimeSum> grouped = TaskPrefixGrouper.groupByPrefix(sums);
		assertEquals("0:20", grouped.get("").toString());
	}

	@Test
	public void groupByPrefixDoesNotMutateInputSums() {
		Map<Task, TimeSum> sums = new HashMap<>();
		TimeSum original = timeSum(1, 0);
		sums.put(task(1, "vrt foo"), original);
		sums.put(task(2, "vrt bar"), timeSum(1, 0));

		TaskPrefixGrouper.groupByPrefix(sums);

		assertEquals("1:00", original.toString());
	}

	@Test
	public void groupPerRangeGroupsEachPeriodSeparately() {
		ZonedDateTime day1 = ZonedDateTime.of(2026, 6, 9, 0, 0, 0, 0, ZoneId.of("Europe/Helsinki"));
		ZonedDateTime day2 = day1.plusDays(1);

		Map<Task, TimeSum> day1Sums = new HashMap<>();
		day1Sums.put(task(1, "vrt foo"), timeSum(1, 0));
		day1Sums.put(task(2, "vrt bar"), timeSum(2, 0));
		Map<Task, TimeSum> day2Sums = new HashMap<>();
		day2Sums.put(task(2, "vrt bar"), timeSum(0, 45));

		Map<ZonedDateTime, Map<Task, TimeSum>> perRange = new HashMap<>();
		perRange.put(day1, day1Sums);
		perRange.put(day2, day2Sums);

		Map<ZonedDateTime, Map<String, TimeSum>> grouped = TaskPrefixGrouper.groupPerRange(perRange);

		assertEquals(2, grouped.size());
		assertEquals("3:00", grouped.get(day1).get("vrt").toString());
		assertEquals("0:45", grouped.get(day2).get("vrt").toString());
	}
}
