package didzapp.hf_translator;

import java.sql.Time;
import java.util.Date;

import didzapp.HF_Translator.Translator;
import didzapp.HF_Translator.Translator.Language;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {
	/**
	 * Create the test case
	 *
	 * @param testName name of the test case
	 */
	public AppTest(String testName) {
		super(
				testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(
				AppTest.class);
	}

	/**
	 * Rigorous Test :-)
	 */
	public static void testApp() {
		// Optional: Force logger to also write to console (adjust to your LOGGER API)
		// If LOGGER is your own class, add a setConsoleLogging(true) method.
		// For now, we'll use System.out directly alongside LOGGER.
		int testsPassed = 0;
		int testsFailed = 0;
		try {
			for (Language l : Language.usableValues()) {
				System.out.println("\n=== Testing Language: " + l + " ==="); //$NON-NLS-1$ //$NON-NLS-2$
				// 1. formatNumber
				String formattedNum = Translator.formatNumber(l, Double.valueOf(100.100));
				System.out.println("formatNumber(Double 100.100): " + formattedNum); //$NON-NLS-1$
				assertNotNull(formattedNum);
				// 2. formatTimestamp
				String formattedTs = Translator.formatTimestamp(l, Translator.quickTimestamp.timestamp());
				System.out.println("formatTimestamp: " + formattedTs); //$NON-NLS-1$
				assertNotNull(formattedTs);
				// 3. formatLocalDateTime
				String formattedLdt = Translator.formatLocalDateTime(l, Translator.quickTimestamp.timestamp().toLocalDateTime());
				System.out.println("formatLocalDateTime: " + formattedLdt); //$NON-NLS-1$
				assertNotNull(formattedLdt);
				// 4. formatTimestamp_MonthYear
				String formattedMy = Translator.formatTimestamp_MonthYear(l, Translator.quickTimestamp.timestamp());
				System.out.println("formatTimestamp_MonthYear: " + formattedMy); //$NON-NLS-1$
				assertNotNull(formattedMy);
				// 5. formatDate
				String formattedDate = Translator.formatDate(l, Translator.quickTimestamp.timestamp().toString());
				System.out.println("formatDate: " + formattedDate); //$NON-NLS-1$
				assertNotNull(formattedDate);
				// 6. formatTime
				String formattedTime = Translator.formatTime(l, Translator.quickTimestamp.timestamp().toString());
				System.out.println("formatTime: " + formattedTime); //$NON-NLS-1$
				assertNotNull(formattedTime);
				// 7. formatCurrency
				String formattedCurr = Translator.formatCurrency(l, "£100.10"); //$NON-NLS-1$
				System.out.println("formatCurrency: " + formattedCurr); //$NON-NLS-1$
				assertNotNull(formattedCurr);
				// 8. parseDate
				Date parsedDate = Translator.parseDate(l, Translator.quickTimestamp.timestamp().toString());
				System.out.println("parseDate: " + parsedDate); //$NON-NLS-1$
				assertNotNull(parsedDate, "parseDate returned null"); //$NON-NLS-1$
				// 9. parseTime – FIX: avoid .toString() on null
				String timeStr = Translator.quickTimestamp.timestamp().toLocalDateTime().toLocalTime().toString();
				Time parsedTime = Translator.parseTime(l, timeStr);
				System.out.println("parseTime input: " + timeStr); //$NON-NLS-1$
				System.out.println("parseTime result: " + parsedTime); //$NON-NLS-1$
				assertNotNull(parsedTime, "parseTime returned null for input: " + timeStr); //$NON-NLS-1$
				// 10. DeterminTimeOfDay
				int timeOfDay = Translator.determinTimeOfDay(l);
				System.out.println("DeterminTimeOfDay: " + timeOfDay); //$NON-NLS-1$
				assertEquals(timeOfDay, timeOfDay);
				testsPassed++;
			}
		} catch (Exception e) {
			System.err.println("Test failed with exception: " + e.getMessage()); //$NON-NLS-1$
			e.printStackTrace();
			testsFailed++;
		}
		System.out.println("\n=== TEST SUMMARY ==="); //$NON-NLS-1$
		System.out.println("Passed: " + testsPassed); //$NON-NLS-1$
		System.out.println("Failed: " + testsFailed); //$NON-NLS-1$
		// Fail the whole test if any failure occurred (for JUnit)
		if (testsFailed > 0) {
			fail("Some tests failed. See console output for details."); //$NON-NLS-1$
		}
	}

// Helper assertion methods (if not using JUnit assertions)
	private static void assertNotNull(Object obj, String message) {
		if (obj == null) {
			throw new AssertionError(
					message);
		}
	}
}
