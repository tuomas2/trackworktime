package org.zephyrsoft.trackworktime.report;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.util.Function;
import org.junit.Test;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TimeSum;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class CsvGeneratorSumsTest {

	private final CsvGenerator csvGenerator = new CsvGenerator(null, null);

	// Mirrors the private constant in ReportsActivity
	private static final Function<Task, String> TASK_WITH_ID =
		task -> task.getName() + " (ID=" + task.getId() + ")";

	private static TimeSum timeSum(int hours, int minutes) {
		TimeSum sum = new TimeSum();
		sum.set(hours, minutes);
		return sum;
	}

	@Test
	public void createSumsCsvUsesExtractorForTaskColumn() {
		Map<Task, TimeSum> sums = new HashMap<>();
		sums.put(new Task(7, "vrt foo", 1, 0, 0), timeSum(1, 30));

		String csv = csvGenerator.createSumsCsv(sums,
			task -> task.getName() + " (ID=" + task.getId() + ")");

		assertTrue(csv.contains("task;spent"));
		assertTrue(csv.contains("vrt foo (ID=7);1:30"));
	}

	@Test
	public void createSumsCsvWithStringKeysHasNoIdSuffix() {
		Map<String, TimeSum> sums = new HashMap<>();
		sums.put("vrt", timeSum(3, 15));

		String csv = csvGenerator.createSumsCsv(sums, prefix -> prefix);

		assertTrue(csv.contains("vrt;3:15"));
		assertFalse(csv.contains("ID="));
	}

	@Test
	public void createSumsPerDayCsvUsesExtractor() {
		ZonedDateTime day = ZonedDateTime.of(2026, 6, 9, 0, 0, 0, 0, ZoneId.of("Europe/Helsinki"));
		Map<String, TimeSum> daySums = new HashMap<>();
		daySums.put("vrt", timeSum(2, 0));
		Map<ZonedDateTime, Map<String, TimeSum>> sumsPerRange = new HashMap<>();
		sumsPerRange.put(day, daySums);

		String csv = csvGenerator.createSumsPerDayCsv(sumsPerRange, prefix -> prefix);

		assertTrue(csv.contains("day;task;spent"));
		assertTrue(csv.contains("2026-06-09;vrt;2:00"));
	}

	@Test
	public void createSumsCsvAddsDecimalHoursColumnByDefault() {
		Map<Task, TimeSum> sums = new HashMap<>();
		sums.put(new Task(7, "vrt foo", 1, 0, 0), timeSum(1, 30));

		String csv = csvGenerator.createSumsCsv(sums, TASK_WITH_ID);

		assertTrue(csv.contains("task;spent;spentHours"));
		// 1:30 == 1.5 hours, default is a period separator with 2 decimals
		assertTrue(csv.contains("vrt foo (ID=7);1:30;1.50"));
	}

	@Test
	public void decimalHoursColumnRespectsConfiguredSeparator() {
		CsvGenerator generator = new CsvGenerator(null, null, ",", 2);
		Map<String, TimeSum> sums = new HashMap<>();
		sums.put("vrt", timeSum(1, 30));

		String csv = generator.createSumsCsv(sums, prefix -> prefix);

		assertTrue(csv.contains("vrt;1:30;1,50"));
	}

	@Test
	public void decimalHoursColumnRespectsConfiguredNumberOfPlaces() {
		CsvGenerator generator = new CsvGenerator(null, null, ".", 4);
		Map<String, TimeSum> sums = new HashMap<>();
		// 0:20 == 0.3333... hours
		sums.put("vrt", timeSum(0, 20));

		String csv = generator.createSumsCsv(sums, prefix -> prefix);

		assertTrue(csv.contains("vrt;0:20;0.3333"));
	}

	@Test
	public void createSumsPerDayCsvAddsDecimalHoursColumn() {
		ZonedDateTime day = ZonedDateTime.of(2026, 6, 9, 0, 0, 0, 0, ZoneId.of("Europe/Helsinki"));
		Map<String, TimeSum> daySums = new HashMap<>();
		daySums.put("vrt", timeSum(2, 0));
		Map<ZonedDateTime, Map<String, TimeSum>> sumsPerRange = new HashMap<>();
		sumsPerRange.put(day, daySums);

		String csv = csvGenerator.createSumsPerDayCsv(sumsPerRange, prefix -> prefix);

		assertTrue(csv.contains("day;task;spent;spentHours"));
		assertTrue(csv.contains("2026-06-09;vrt;2:00;2.00"));
	}

	@Test
	public void dayCountReportHasNoDecimalHoursColumn() {
		ZonedDateTime week = ZonedDateTime.of(2026, 6, 8, 0, 0, 0, 0, ZoneId.of("Europe/Helsinki"));
		Map<String, Integer> weekCounts = new HashMap<>();
		weekCounts.put("flexi", 3);
		Map<ZonedDateTime, Map<String, Integer>> sumsPerRange = new HashMap<>();
		sumsPerRange.put(week, weekCounts);

		String csv = csvGenerator.createDayCountPerWeekCsv(sumsPerRange,
			new String[] { "week", "target", "days" });

		assertTrue(csv.contains("week;target;days"));
		assertFalse(csv.contains("spentHours"));
	}

	@Test
	public void createSumsCsvHandlesNullKey() {
		Map<Task, TimeSum> sums = new HashMap<>();
		sums.put(null, timeSum(0, 30));

		String csv = csvGenerator.createSumsCsv(sums, TASK_WITH_ID);

		// The row for the null key has an empty task column
		String[] lines = csv.split("\n");
		boolean foundEmptyTaskRow = false;
		for (String line : lines) {
			if (line.trim().startsWith(";0:30")) {
				foundEmptyTaskRow = true;
				break;
			}
		}
		assertTrue("CSV should contain row with empty task column and 0:30 time", foundEmptyTaskRow);
	}
}
