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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.arch.core.util.Function;
import androidx.preference.PreferenceManager;

import org.pmw.tinylog.Logger;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.util.CsvContext;
import org.zephyrsoft.trackworktime.database.DAO;
import org.zephyrsoft.trackworktime.model.Event;
import org.zephyrsoft.trackworktime.model.Target;
import org.zephyrsoft.trackworktime.model.TargetWrapper;
import org.zephyrsoft.trackworktime.model.Task;
import org.zephyrsoft.trackworktime.model.TimeSum;
import org.zephyrsoft.trackworktime.model.TypeEnum;
import org.zephyrsoft.trackworktime.options.Key;
import org.zephyrsoft.trackworktime.util.DateTimeUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Creates CSV reports from events.
 */
public class CsvGenerator {

	/** header name of the additional column that shows the worked time as decimal hours */
	private static final String SPENT_DECIMAL_HEADER = "spentHours";
	private static final String DEFAULT_DECIMAL_SEPARATOR = ".";
	private static final int DEFAULT_DECIMAL_PLACES = 2;

	private final DAO dao;
	private final Context context;
	private final String decimalSeparator;
	private final int decimalPlaces;

	public CsvGenerator(DAO dao, Context context) {
		this(dao, context, readDecimalSeparator(context), readDecimalPlaces(context));
	}

	/** Visible for testing: inject the decimal-hours formatting config directly (no Context needed). */
	CsvGenerator(DAO dao, Context context, String decimalSeparator, int decimalPlaces) {
		this.dao = dao;
		this.context = context;
		this.decimalSeparator = decimalSeparator;
		this.decimalPlaces = Math.max(0, decimalPlaces);
	}

	private static String readDecimalSeparator(Context context) {
		if (context == null) {
			return DEFAULT_DECIMAL_SEPARATOR;
		}
		return PreferenceManager.getDefaultSharedPreferences(context)
			.getString(Key.CSV_DECIMAL_SEPARATOR.getName(), DEFAULT_DECIMAL_SEPARATOR);
	}

	private static int readDecimalPlaces(Context context) {
		if (context == null) {
			return DEFAULT_DECIMAL_PLACES;
		}
		String value = PreferenceManager.getDefaultSharedPreferences(context)
			.getString(Key.CSV_DECIMAL_PLACES.getName(), String.valueOf(DEFAULT_DECIMAL_PLACES));
		try {
			return Math.max(0, Integer.parseInt(value.trim()));
		} catch (NumberFormatException e) {
			return DEFAULT_DECIMAL_PLACES;
		}
	}

	/** Formats a worked-time sum as decimal hours, honoring the configured separator and precision. */
	private String formatDecimalHours(TimeSum timeSum) {
		double hours = timeSum.getAsMinutes() / 60.0;
		String formatted = String.format(Locale.US, "%." + decimalPlaces + "f", hours);
		if (!DEFAULT_DECIMAL_SEPARATOR.equals(decimalSeparator)) {
			formatted = formatted.replace(DEFAULT_DECIMAL_SEPARATOR, decimalSeparator);
		}
		return formatted;
	}

	/** Cell processor that renders a {@link TimeSum} as decimal hours for the extra column. */
	private CellProcessor decimalHoursProcessor() {
		return new CellProcessorAdaptor() {
			@Override
			public Object execute(Object arg0, CsvContext arg1) {
				if (arg0 == null) {
					throw new IllegalStateException("time sum may not be null");
				} else {
					return formatDecimalHours((TimeSum) arg0);
				}
			}
		};
	}

	/** Appends the decimal-hours column to the display header. */
	private static String[] withDecimalHeader(String[] header) {
		String[] result = Arrays.copyOf(header, header.length + 1);
		result[header.length] = SPENT_DECIMAL_HEADER;
		return result;
	}

	/**
	 * Builds the bean field mapping for the extra column by re-reading the "spent" property
	 * (the last column of every worked-time report), so the decimal column reuses the same TimeSum.
	 */
	private static String[] withDecimalMapping(String[] header) {
		String[] result = Arrays.copyOf(header, header.length + 1);
		result[header.length] = header[header.length - 1];
		return result;
	}

	/** Appends the decimal-hours cell processor to an existing processor array. */
	private CellProcessor[] withDecimalProcessor(CellProcessor[] processors) {
		CellProcessor[] result = Arrays.copyOf(processors, processors.length + 1);
		result[processors.length] = decimalHoursProcessor();
		return result;
	}

	/** time, type, task, text */
	@SuppressWarnings("unchecked")
	private CellProcessor[] getEventProcessors() {
		return new CellProcessor[]{
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						throw new IllegalStateException("event time may not be null");
					} else {
						return ((OffsetDateTime) arg0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
					}
				}
			},
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						throw new IllegalStateException("event type may not be null");
					} else {
						return TypeEnum.byValue((Integer) arg0).getReadableName(context);
					}
				}
			},
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						return null;
					} else {
						Task task = dao.getTask((Integer) arg0);
						return task == null ? "" : task.getName();
					}
				}
			},
			new Optional()
		};
	}

	/**
	 * date, type, value, comment
	 */
	@SuppressWarnings("unchecked")
	private CellProcessor[] getTargetProcessors() {
		return new CellProcessor[]{
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						throw new IllegalStateException("target date may not be null");
					} else {
						return ((LocalDate) arg0).format(DateTimeFormatter.ISO_LOCAL_DATE);
					}
				}
			},
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						throw new IllegalStateException("target type may not be null");
					} else {
						return arg0;
					}
				}
			},
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null || (arg0 instanceof Integer && ((Integer) arg0) == 0)) {
						return null;
					} else if (arg0 instanceof Integer) {
						return DateTimeUtil.formatDuration((Integer) arg0);
					} else {
						return null;
					}
				}
			},
			new Optional()
		};
	}

	/** task, spent */
	@SuppressWarnings("unchecked")
	private CellProcessor[] getSumsProcessors() {
		return new CellProcessor[] {
			new NotNull(),
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						throw new IllegalStateException("time sum may not be null");
					} else {
						return arg0.toString();
					}
				}
			}
		};
	}

	/** task, text, spent */
	@SuppressWarnings("unchecked")
	private CellProcessor[] getSumsAndHintsProcessors() {
		return new CellProcessor[] {
				new NotNull(),
				new NotNull(),
				new CellProcessorAdaptor() {
					@Override
					public Object execute(Object arg0, CsvContext arg1) {
						if (arg0 == null) {
							throw new IllegalStateException("time sum may not be null");
						} else {
							return arg0.toString();
						}
					}
				}
		};
	}

	/** (day|month|week), task, spent */
	@SuppressWarnings("unchecked")
	private CellProcessor[] getSumsPerRangeProcessors() {
		return new CellProcessor[]{
			new NotNull(),
			new NotNull(),
			new CellProcessorAdaptor() {
				@Override
				public Object execute(Object arg0, CsvContext arg1) {
					if (arg0 == null) {
						throw new IllegalStateException("time sum may not be null");
					} else {
						return arg0.toString();
					}
				}
			}
		};
	}

	/** (day|month|week), task, text, spent */
	@SuppressWarnings("unchecked")
	private CellProcessor[] getSumsPerRangeWithHintsProcessors() {
		return new CellProcessor[]{
				new NotNull(),
				new NotNull(),
				new NotNull(),
				new CellProcessorAdaptor() {
					@Override
					public Object execute(Object arg0, CsvContext arg1) {
						if (arg0 == null) {
							throw new IllegalStateException("time sum may not be null");
						} else {
							return arg0.toString();
						}
					}
				}
		};
	}

	/**
	 * Warning: could modify the provided event list!
	 */
	public String createTargetCsv(List<Target> targets) {
		ICsvBeanWriter beanWriter = null;
		StringWriter resultWriter = new StringWriter();
		try {
			beanWriter = new CsvBeanWriter(resultWriter, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);

			// the header elements are used to map the bean values to each column (names must match!)
			final String[] header = new String[] { "date", "type", "value", "comment" };

			beanWriter.writeHeader(header);

			CellProcessor[] targetProcessors = getTargetProcessors();
			for (Target target : targets) {
				beanWriter.write(new TargetWrapper(target), header, targetProcessors);
			}
		} catch (IOException e) {
			Logger.error(e, "error while writing");
		} finally {
			if (beanWriter != null) {
				try {
					beanWriter.close();
				} catch (IOException e) {
					// do nothing
				}
			}
		}
		return resultWriter.toString();
	}

	/**
	 * Warning: could modify the provided event list!
	 */
	public String createEventCsv(List<Event> events) {
		ICsvBeanWriter beanWriter = null;
		StringWriter resultWriter = new StringWriter();
		try {
			beanWriter = new CsvBeanWriter(resultWriter, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);

			// the header elements are used to map the bean values to each column (names must match!)
			final String[] header = new String[] { "time", "type", "task", "text" };

			beanWriter.writeHeader(header);

			CellProcessor[] eventProcessors = getEventProcessors();
			for (Event event : events) {
				// "clock out" events shouldn't have a task and text:
				if (TypeEnum.byValue(event.getType()) == TypeEnum.CLOCK_OUT) {
					event.setTask(null);
					event.setText(null);
				}
				beanWriter.write(event, header, eventProcessors);
			}
		} catch (IOException e) {
			Logger.error(e, "error while writing");
		} finally {
			if (beanWriter != null) {
				try {
					beanWriter.close();
				} catch (IOException e) {
					// do nothing
				}
			}
		}
		return resultWriter.toString();
	}

	public <T> String createSumsCsv(Map<T, TimeSum> sums, Function<T, String> extractor) {
		List<TimeSumsHolder> prepared = new ArrayList<>();
		for (Entry<T, TimeSum> entry : sums.entrySet()) {
			String task = "";
			if (entry.getKey() != null) {
				task = extractor.apply(entry.getKey());
			}
			prepared.add(new TimeSumsHolder(null, null, null, task, entry.getValue()));
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, new String[] { "task", "spent" }, getSumsProcessors());
	}

	public String createSumsCsvWithHints(Map<TaskAndHint, TimeSum> sums) {
		List<TimeSumsAndHintsHolder> prepared = new ArrayList<>();
		for (Entry<TaskAndHint, TimeSum> entry : sums.entrySet()) {
			String task = "";
			String hint = "";
			if (entry.getKey() != null) {
				if (entry.getKey().getTask() != null) {
					task = entry.getKey().getTask().getName() + " (ID=" + entry.getKey().getTask().getId() + ")";
				}
				if (entry.getKey().getText() != null) {
					hint = entry.getKey().getText();
				}
			}
			prepared.add(new TimeSumsAndHintsHolder(null, null, null, task, hint, entry.getValue()));
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, new String[] { "task", "text", "spent" }, getSumsAndHintsProcessors());
	}

	public <T> String createSumsPerDayCsv(Map<ZonedDateTime, Map<T, TimeSum>> sumsPerRange,
										  Function<T, String> extractor) {
		List<TimeSumsHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<T, TimeSum>> rangeEntry : sumsPerRange.entrySet()) {
			String day = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<T, TimeSum> sums = rangeEntry.getValue();
			for (Entry<T, TimeSum> entry : sums.entrySet()) {
				String task = "";
				if (entry.getKey() != null) {
					task = extractor.apply(entry.getKey());
				}
				prepared.add(TimeSumsHolder.createForDay(day, task, entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, new String[] { "day", "task", "spent" }, getSumsPerRangeProcessors());
	}

	public String createSumsWithHintsPerDayCsv(Map<ZonedDateTime, Map<TaskAndHint, TimeSum>> sumsPerRange) {
		List<TimeSumsAndHintsHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<TaskAndHint, TimeSum>> rangeEntry : sumsPerRange.entrySet()) {
			String day = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<TaskAndHint, TimeSum> sums = rangeEntry.getValue();
			for (Entry<TaskAndHint, TimeSum> entry : sums.entrySet()) {
				String task = "";
				String hint = "";
				if (entry.getKey() != null) {
					if (entry.getKey().getTask() != null) {
						task = entry.getKey().getTask().getName() + " (ID=" + entry.getKey().getTask().getId() + ")";
					}
					if (entry.getKey().getText() != null) {
						hint = entry.getKey().getText();
					}
				}
				prepared.add(TimeSumsAndHintsHolder.createForDay(day, task, hint, entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, new String[] { "day", "task", "text", "spent" }, getSumsPerRangeWithHintsProcessors());
	}

	public <T> String createSumsPerWeekCsv(Map<ZonedDateTime, Map<T, TimeSum>> sumsPerRange,
										   String[] header, Function<T, String> extractor) {
		List<TimeSumsHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<T, TimeSum>> rangeEntry : sumsPerRange.entrySet()) {
			String week = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<T, TimeSum> sums = rangeEntry.getValue();
			for (Entry<T, TimeSum> entry : sums.entrySet()) {
				String key = "";
				if (entry.getKey() != null) {
					key = extractor.apply(entry.getKey());
				}
				prepared.add(TimeSumsHolder.createForWeek(week, key, entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, header, getSumsPerRangeProcessors());
	}

	public String createSumsWithHintsPerWeeksCsv(Map<ZonedDateTime, Map<TaskAndHint, TimeSum>> sumsPerRange,
												 String[] header, Function<TaskAndHint, String> keyExtractor,
												 Function<TaskAndHint, String> hintExtractor) {
		List<TimeSumsAndHintsHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<TaskAndHint, TimeSum>> rangeEntry : sumsPerRange.entrySet()) {
			String week = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<TaskAndHint, TimeSum> sums = rangeEntry.getValue();
			for (Entry<TaskAndHint, TimeSum> entry : sums.entrySet()) {
				String key = "";
				String hint = "";
				if (entry.getKey() != null) {
					if (entry.getKey().getTask() != null) {
						key = keyExtractor.apply(entry.getKey());
					}
					if (entry.getKey().getText() != null) {
						hint = hintExtractor.apply(entry.getKey());
					}
				}
				prepared.add(TimeSumsAndHintsHolder.createForWeek(week, key, hint, entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, header, getSumsPerRangeWithHintsProcessors());
	}

	public String createDayCountPerWeekCsv(Map<ZonedDateTime, Map<String, Integer>> sumsPerRange, String[] header) {
		List<TargetDaysHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<String, Integer>> rangeEntry : sumsPerRange.entrySet()) {
			String week = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<String, Integer> sums = rangeEntry.getValue();
			for (Entry<String, Integer> entry : sums.entrySet()) {
				prepared.add(TargetDaysHolder.createForWeek(week, entry.getKey(), entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createCsv(prepared, header, getSumsPerRangeProcessors());
	}

	public <T> String createSumsPerMonthCsv(Map<ZonedDateTime, Map<T, TimeSum>> sumsPerRange,
											String[] header, Function<T, String> extractor) {
		List<TimeSumsHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<T, TimeSum>> rangeEntry : sumsPerRange.entrySet()) {
			String month = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<T, TimeSum> sums = rangeEntry.getValue();
			for (Entry<T, TimeSum> entry : sums.entrySet()) {
				String task = "";
				if (entry.getKey() != null) {
					task = extractor.apply(entry.getKey());
				}
				prepared.add(TimeSumsHolder.createForMonth(month, task, entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, header, getSumsPerRangeProcessors());
	}

	public String createSumsWithHintsPerMonthCsv(Map<ZonedDateTime, Map<TaskAndHint, TimeSum>> sumsPerRange,
											String[] header, Function<TaskAndHint, String> keyExtractor,
										Function<TaskAndHint, String> hintExtractor) {
		List<TimeSumsAndHintsHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<TaskAndHint, TimeSum>> rangeEntry : sumsPerRange.entrySet()) {
			String month = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<TaskAndHint, TimeSum> sums = rangeEntry.getValue();
			for (Entry<TaskAndHint, TimeSum> entry : sums.entrySet()) {
				String task = "";
				String hint = "";
				if (entry.getKey() != null) {
					if (entry.getKey().getTask() != null) {
						task = keyExtractor.apply(entry.getKey());
					}
					if (entry.getKey().getText() != null) {
						hint = hintExtractor.apply(entry.getKey());
					}
				}
				prepared.add(TimeSumsAndHintsHolder.createForMonth(month, task, hint, entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createWorkedTimeCsv(prepared, header, getSumsPerRangeWithHintsProcessors());
	}

	public String createDayCountPerMonthCsv(Map<ZonedDateTime, Map<String, Integer>> sumsPerRange, String[] header) {
		List<TargetDaysHolder> prepared = new ArrayList<>();
		for (Entry<ZonedDateTime, Map<String, Integer>> rangeEntry : sumsPerRange.entrySet()) {
			String month = DateTimeUtil.dateToULString(rangeEntry.getKey());
			Map<String, Integer> sums = rangeEntry.getValue();
			for (Entry<String, Integer> entry : sums.entrySet()) {
				prepared.add(TargetDaysHolder.createForMonth(month, entry.getKey(), entry.getValue()));
			}
		}
		Collections.sort(prepared);

		return createCsv(prepared, header, getSumsPerRangeProcessors());
	}

	private String createCsv(List<?> dataToWrite, String[] header, CellProcessor[] processors) {
		return createCsv(dataToWrite, header, header, processors);
	}

	/**
	 * Like {@link #createCsv}, but appends the decimal-hours column. {@code header} must end with the
	 * "spent" (TimeSum) column, and {@code processors} must match {@code header}.
	 */
	private String createWorkedTimeCsv(List<?> dataToWrite, String[] header, CellProcessor[] processors) {
		return createCsv(dataToWrite, withDecimalHeader(header), withDecimalMapping(header),
			withDecimalProcessor(processors));
	}

	/**
	 * @param header
	 *            the column labels written as the first CSV line
	 * @param nameMapping
	 *            the bean property names used to read each column value (names must match getters!);
	 *            may differ from {@code header} when a column re-reads a property under a new label
	 */
	private String createCsv(List<?> dataToWrite, String[] header, String[] nameMapping, CellProcessor[] processors) {
		ICsvBeanWriter beanWriter = null;
		StringWriter resultWriter = new StringWriter();
		try {
			beanWriter = new CsvBeanWriter(resultWriter, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);

			beanWriter.writeHeader(header);

			for (Object dataElement : dataToWrite) {
				beanWriter.write(dataElement, nameMapping, processors);
			}
		} catch (IOException e) {
			Logger.error(e, "error while writing");
		} finally {
			if (beanWriter != null) {
				try {
					beanWriter.close();
				} catch (IOException e) {
					// do nothing
				}
			}
		}
		return resultWriter.toString();
	}

}
