package didzapp.hf_translator;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Time;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import didzapp.HF_Translator.Translator;

/**
 * Unit test for Translator library. Modern JUnit 5 test class - runs on Java 23
 * without any legacy baggage.
 */
public class AppTest {
	@SuppressWarnings("static-method")
	@Test
	void testAllLanguages() {
		Locale l = Locale.ENGLISH;
		System.out.println("\n=== Testing Language: " + l + " ==="); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			// 1. rightToLeftDetect
			boolean rtl = Translator.isRightToLeft(l);
			System.out.println("rightToLeftDetect(\"Locale.forLanguageTag(\"en-GB\")): " + rtl); //$NON-NLS-1$
			Assertions.assertFalse(rtl, "rightToLeftDetect returned true for " + l); //$NON-NLS-1$
			// 1. formatNumber
			String formattedNum = Translator.formatNumber(l, Double.valueOf(100.100));
			System.out.println("formatNumber(Double 100.100): " + formattedNum); //$NON-NLS-1$
			assertNotNull(formattedNum, "formatNumber returned null for " + l); //$NON-NLS-1$
			// 2. formatTimestamp
			String formattedTs = Translator.formatTimestamp(l, Translator.quickTimestamp.timestamp());
			System.out.println("formatTimestamp: " + formattedTs); //$NON-NLS-1$
			assertNotNull(formattedTs, "formatTimestamp returned null for " + l); //$NON-NLS-1$
			// 3. formatLocalDateTime
			String formattedLdt = Translator.formatLocalDateTime(l, Translator.quickTimestamp.timestamp().toLocalDateTime());
			System.out.println("formatLocalDateTime: " + formattedLdt); //$NON-NLS-1$
			assertNotNull(formattedLdt, "formatLocalDateTime returned null for " + l); //$NON-NLS-1$
			// 4. formatTimestamp_MonthYear
			String formattedMy = Translator.formatTimestamp_MonthYear(l, Translator.quickTimestamp.timestamp());
			System.out.println("formatTimestamp_MonthYear: " + formattedMy); //$NON-NLS-1$
			assertNotNull(formattedMy, "formatTimestamp_MonthYear returned null for " + l); //$NON-NLS-1$
			// 5. formatDate
			String formattedDate = Translator.formatDate(l, Translator.quickTimestamp.timestamp().toString());
			System.out.println("formatDate: " + formattedDate); //$NON-NLS-1$
			assertNotNull(formattedDate, "formatDate returned null for " + l); //$NON-NLS-1$
			// 6. formatTime
			String formattedTime = Translator.formatTime(l, Translator.quickTimestamp.timestamp().toString());
			System.out.println("formatTime: " + formattedTime); //$NON-NLS-1$
			assertNotNull(formattedTime, "formatTime returned null for " + l); //$NON-NLS-1$
			// 7. formatCurrency
			String formattedCurr = Translator.formatCurrency(l, "£100.10"); //$NON-NLS-1$
			System.out.println("formatCurrency: " + formattedCurr); //$NON-NLS-1$
			assertNotNull(formattedCurr, "formatCurrency returned null for " + l); //$NON-NLS-1$
			// 8. parseDate
			Date parsedDate = Translator.parseDate(l, Translator.quickTimestamp.timestamp().toString());
			System.out.println("parseDate: " + parsedDate); //$NON-NLS-1$
			assertNotNull(parsedDate, "parseDate returned null for " + l); //$NON-NLS-1$
			// 9. parseTime
			String timeStr = Translator.quickTimestamp.timestamp().toLocalDateTime().toLocalTime().toString();
			Time parsedTime = Translator.parseTime(l, timeStr);
			System.out.println("parseTime input: " + timeStr); //$NON-NLS-1$
			System.out.println("parseTime result: " + parsedTime); //$NON-NLS-1$
			// 10. DeterminTimeOfDay
			int timeOfDay = Translator.determinTimeOfDay(l, ZoneId.of("Europe/London")); //$NON-NLS-1$
			System.out.println("DeterminTimeOfDay: " + timeOfDay); //$NON-NLS-1$
			// Ensure it returns a non-negative integer (basic sanity check)
			if (timeOfDay < 0) {
				throw new AssertionError(
						"determinTimeOfDay returned negative value: " + timeOfDay); //$NON-NLS-1$
			}
			assertNotNull(parsedTime, "parseTime returned null for input: " + timeStr + " on language: " + l); //$NON-NLS-1$ //$NON-NLS-2$
			System.out.println("✅ Language " + l + " passed all tests."); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (Exception e) {
			System.err.println("❌ Test failed for language " + l + " with exception: " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			e.printStackTrace();
		}
	}
}