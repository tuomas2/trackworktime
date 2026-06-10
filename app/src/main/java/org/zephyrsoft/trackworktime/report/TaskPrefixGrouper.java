/*
 * This file is part of TrackWorkTime (TWT).
 *
 * TWT is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License 3.0 as published by
 * the Free Software Foundation.
 *
 * TWT is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License 3.0 for more details.
 *
 * You should have received a copy of the GNU General Public License 3.0
 * along with TWT. If not, see <http://www.gnu.org/licenses/>.
 */
package org.zephyrsoft.trackworktime.report;

import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TimeSum;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Groups per-task time sums by the first word of the task name, e.g. for merging
 * "vrt foo" and "vrt bar" into a single "vrt" report row.
 */
public class TaskPrefixGrouper {

	private TaskPrefixGrouper() {
		// static methods only
	}

	/** the first whitespace-separated word of the trimmed task name, "" for null/blank names */
	public static String prefixOf(String taskName) {
		if (taskName == null) {
			return "";
		}
		String trimmed = taskName.trim();
		for (int i = 0; i < trimmed.length(); i++) {
			if (Character.isWhitespace(trimmed.charAt(i))) {
				return trimmed.substring(0, i);
			}
		}
		return trimmed;
	}

	/**
	 * Merges entries whose task names share a first word. Keyed by String because
	 * Task.equals/hashCode are id-based and synthetic id-less tasks would collide.
	 * The input map and its TimeSum values are not modified.
	 */
	public static Map<String, TimeSum> groupByPrefix(Map<Task, TimeSum> sums) {
		Map<String, TimeSum> grouped = new HashMap<>();
		for (Map.Entry<Task, TimeSum> entry : sums.entrySet()) {
			String prefix = entry.getKey() == null ? "" : prefixOf(entry.getKey().getName());
			TimeSum sum = grouped.get(prefix);
			if (sum == null) {
				sum = new TimeSum();
				grouped.put(prefix, sum);
			}
			sum.addOrSubstract(entry.getValue());
		}
		return grouped;
	}

	public static Map<ZonedDateTime, Map<String, TimeSum>> groupPerRange(
		Map<ZonedDateTime, Map<Task, TimeSum>> sumsPerRange) {
		Map<ZonedDateTime, Map<String, TimeSum>> grouped = new HashMap<>();
		for (Map.Entry<ZonedDateTime, Map<Task, TimeSum>> entry : sumsPerRange.entrySet()) {
			grouped.put(entry.getKey(), groupByPrefix(entry.getValue()));
		}
		return grouped;
	}
}
