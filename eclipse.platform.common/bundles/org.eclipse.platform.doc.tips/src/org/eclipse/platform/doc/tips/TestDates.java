package org.eclipse.platform.doc.tips;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestDates {
	public static void main(String[] args) {
		System.out.println(parseWeekOfYear("I20241228-1800"));
		System.out.println(parseWeekOfYear("I20241229-1800"));
		System.out.println(parseWeekOfYear("I20241230-1800"));
		System.out.println(parseWeekOfYear("I20241231-1800"));
		System.out.println(parseWeekOfYear("I20250101-1800"));
		System.out.println(parseWeekOfYear("I20250102-1800"));
		System.out.println(parseWeekOfYear("I20250103-1800"));
		System.out.println(parseWeekOfYear("I20250104-1800"));
		System.out.println(parseWeekOfYear("I20250105-1800"));
		System.out.println(parseWeekOfYear("I20250106-1800"));
		System.out.println(parseWeekOfYear("I20250107-1800"));
		System.out.println(parseWeekOfYear("I20250108-1800"));
		System.out.println(parseWeekOfYear("I20250109-1800"));
		System.out.println(parseWeekOfYear("I20250110-1800"));
		System.out.println(parseWeekOfYear("I20250111-1800"));
		System.out.println(parseWeekOfYear("I20250112-1800"));
		System.out.println(parseWeekOfYear("I20250113-1800"));
		System.out.println(parseWeekOfYear("I20250114-1800"));

		System.out.println(parseWeekOfYear("I20250911-1800"));
		System.out.println(parseWeekOfYear("I20250912-1800"));
		System.out.println(parseWeekOfYear("I20250913-1800"));
		System.out.println(parseWeekOfYear("I20250914-1800"));
		System.out.println(parseWeekOfYear("I20250915-1800"));
//		Calendar cal = Calendar.getInstance(Locale.ROOT);
//		cal.set(2025, 9, 11);
//		System.out.println(cal.get(Calendar.WEEK_OF_YEAR));
		
		String[] split = "".split("\\s+");
		System.out.println(split);
	}

	private static int parseWeekOfYear(String iBuildId) {
		Matcher idMatcher = Pattern.compile("I(?<date>\\d{8})-(?<time>\\d{4})").matcher(iBuildId);
		if (!idMatcher.matches()) {
			throw new RuntimeException("buildID: ${iBuildId}, does not match the expected pattern.");
		}
		String date = idMatcher.group("date");
		var localDate = java.time.LocalDate
				.parse(date.substring(0, 4) + '-' + date.substring(4, 6) + '-' + date.substring(6, 8));
		int mondayDOY = localDate.getDayOfYear() - localDate.getDayOfWeek().ordinal();
		int weekOfYear = (7 + mondayDOY) / 7;
		return weekOfYear == 53 ? 0 : weekOfYear;

//		return localDate.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
	}

	private static int parseWeekOfYear2(String iBuildId) {
		Matcher idMatcher = Pattern.compile("I(?<date>\\d{8})-(?<time>\\d{4})").matcher(iBuildId);
		if (!idMatcher.matches()) {
			throw new RuntimeException("buildID: ${iBuildId}, does not match the expected pattern.");
		}
		String date = idMatcher.group("date");
		Calendar cal = Calendar.getInstance(Locale.GERMAN);
		cal.set(Integer.parseInt(date.substring(0, 4)), Integer.parseInt(date.substring(4, 6)),
				Integer.parseInt(date.substring(6, 8)));
		cal.setLenient(false);
		cal.setFirstDayOfWeek(Calendar.MONDAY);
		return cal.get(Calendar.WEEK_OF_YEAR);
	}
}
